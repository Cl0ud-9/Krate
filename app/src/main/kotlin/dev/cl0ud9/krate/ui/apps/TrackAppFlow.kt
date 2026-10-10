package dev.cl0ud9.krate.ui.apps

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.cl0ud9.krate.data.downloads.ArtifactDownloader
import dev.cl0ud9.krate.data.tracked.TrackedAppsRepository
import dev.cl0ud9.krate.data.tracked.TrackedEntry
import dev.cl0ud9.krate.data.tracked.buildsFrom
import dev.cl0ud9.krate.data.tracked.toProfile
import dev.cl0ud9.krate.data.tracked.trackedIdFor
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.repository.CatalogRepository
import dev.cl0ud9.krate.domain.tracked.Forge
import dev.cl0ud9.krate.domain.tracked.ReleaseAsset
import dev.cl0ud9.krate.domain.tracked.RepoRef
import dev.cl0ud9.krate.domain.tracked.nameShape
import dev.cl0ud9.krate.domain.tracked.parseRepoLink
import dev.cl0ud9.krate.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.krate.platform.tracked.InspectedApp
import dev.cl0ud9.krate.platform.tracked.Inspection
import dev.cl0ud9.krate.platform.tracked.LookUp
import dev.cl0ud9.krate.platform.tracked.TrackedAppInspector
import dev.cl0ud9.krate.security.hash.hashesMatch
import dev.cl0ud9.krate.ui.util.withMinimumDuration
import dev.cl0ud9.krate.voice.KrateMemory
import dev.cl0ud9.krate.voice.KrateVoice
import dev.cl0ud9.krate.voice.Moment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// where adding a tracked app is up to
sealed interface TrackStep {
    // the link box; a problem from the last try, and an app to open instead when it's one Krate already has
    data class Enter(
        val problem: String? = null,
        val openAppId: String? = null,
    ) : TrackStep

    data object LookingUp : TrackStep

    // a release with several files this phone can run: the best fit is picked already
    data class Choose(
        val found: LookUp.Found,
        val picked: ReleaseAsset,
    ) : TrackStep

    data class Checking(
        val bytes: Long,
        val totalBytes: Long?,
    ) : TrackStep

    data class Confirm(
        val found: LookUp.Found,
        val asset: ReleaseAsset,
        val app: InspectedApp,
        // the copy already on the phone is signed by someone else
        val signedDifferently: Boolean,
    ) : TrackStep

    data object Saving : TrackStep
}

// the steps of adding an app to track, from a pasted link to a saved entry with its file ready to install
@Stable
class TrackAppFlow(
    private val scope: CoroutineScope,
    private val inspector: TrackedAppInspector,
    private val tracked: TrackedAppsRepository,
    private val catalog: CatalogRepository,
    private val installed: InstalledPackageReader,
    private val downloader: ArtifactDownloader,
) {
    var step: TrackStep by mutableStateOf(TrackStep.Enter())
        private set

    private var work: Job? = null

    fun lookUp(link: String) {
        val ref = parseRepoLink(link)
        if (ref == null) {
            step =
                TrackStep.Enter(
                    deadEnd(
                        "That doesn't look like a repository on GitHub, Codeberg or GitLab. Try a link like " +
                            "github.com/owner/app.",
                    ),
                )
            return
        }
        work?.cancel()
        work =
            scope.launch {
                // what Krate already knows answers at once, with no spinner flashing up and away first
                val already =
                    tracked.entries().find {
                        it.ref.forge == ref.forge &&
                            it.ref.slug.equals(ref.slug, ignoreCase = true)
                    }
                if (already != null) {
                    step = TrackStep.Enter(alreadyHere("You're already tracking ${already.displayName}."), already.id)
                    return@launch
                }
                // one the catalog already carries from this repository: no need to download anything to know. The
                // catalog's apps all come from GitHub
                val listed =
                    catalog
                        .observeApps()
                        .first()
                        .filterNot { it.trackedRepo != null }
                        .takeIf { ref.forge == Forge.GITHUB }
                        ?.publishedFrom(ref.slug)
                if (listed != null) {
                    step =
                        TrackStep.Enter(alreadyHere("It's already in the Krate as ${listed.displayName}."), listed.id)
                    return@launch
                }
                step = TrackStep.LookingUp
                step =
                    when (val result = withMinimumDuration(MIN_SPINNER_MS) { inspector.lookUp(ref) }) {
                        is LookUp.Found -> {
                            if (result.candidates.size == 1) {
                                check(result, result.candidates.single())
                                return@launch
                            }
                            TrackStep.Choose(result, result.candidates.first())
                        }
                        else -> TrackStep.Enter(deadEnd(missedLookUp(result, ref)))
                    }
            }
    }

    fun pick(asset: ReleaseAsset) {
        (step as? TrackStep.Choose)?.let { step = it.copy(picked = asset) }
    }

    fun usePicked() {
        val choose = step as? TrackStep.Choose ?: return
        work?.cancel()
        work = scope.launch { check(choose.found, choose.picked) }
    }

    private suspend fun check(
        found: LookUp.Found,
        asset: ReleaseAsset,
    ) {
        step = TrackStep.Checking(0L, asset.sizeBytes.takeIf { it > 0 })
        inspector.inspect(asset).collect { inspection ->
            when (inspection) {
                is Inspection.Downloading -> step = TrackStep.Checking(inspection.bytes, inspection.totalBytes)
                Inspection.Reading -> Unit
                is Inspection.Failed -> step = TrackStep.Enter(deadEnd(inspection.reason))
                is Inspection.Ready -> step = verdict(found, asset, inspection.app)
            }
        }
    }

    // one Krate already has, by package, gives way to that one; a copy on the phone signed by someone else is warned of
    private suspend fun verdict(
        found: LookUp.Found,
        asset: ReleaseAsset,
        app: InspectedApp,
    ): TrackStep {
        val existing = catalog.observeApps().first().find { it.packageName == app.packageName }
        if (existing != null) {
            inspector.discard(app)
            val how = if (existing.trackedRepo != null) "You already track it" else "It's already in the Krate"
            return TrackStep.Enter(alreadyHere("$how as ${existing.displayName}."), existing.id)
        }
        val signer = installed.installedVersion(app.packageName)?.signerSha256
        val signedDifferently = signer != null && !hashesMatch(app.certificateSha256, signer)
        return TrackStep.Confirm(found, asset, app, signedDifferently)
    }

    // saves it with its builds, and hands the checked file over as the newest build's download
    fun confirm(onTracked: (String) -> Unit) {
        val confirm = step as? TrackStep.Confirm ?: return
        step = TrackStep.Saving
        scope.launch {
            val found = confirm.found
            val shape = nameShape(confirm.asset.name)
            val entry =
                TrackedEntry(
                    id = trackedIdFor(found.ref),
                    owner = found.ref.owner,
                    repo = found.ref.repo,
                    packageName = confirm.app.packageName,
                    displayName = confirm.app.label,
                    description = found.description,
                    iconPng = confirm.app.iconPng,
                    certificateSha256 = confirm.app.certificateSha256,
                    fileShape = shape,
                    addedAtMillis = System.currentTimeMillis(),
                    // a project that only puts out test releases starts with them on, or there'd be nothing to install
                    includePrerelease = found.release.prerelease,
                    builds =
                        buildsFrom(found.releases, shape, found.release.prerelease, inspector.supportedAbis),
                    etag = found.etag,
                    forge = found.ref.forge.id,
                )
            tracked.add(entry)
            KrateMemory.noteFind()
            val profile = entry.toProfile()
            val artifact = profile.artifacts.find { it.buildId == found.release.tag }
            if (artifact !=
                null
            ) {
                downloader.adoptReadyFile(profile, artifact, confirm.app.file)
            } else {
                inspector.discard(confirm.app)
            }
            onTracked(entry.id)
        }
    }

    // back to the link box, letting go of anything downloaded for a choice not made
    fun startOver() {
        work?.cancel()
        (step as? TrackStep.Confirm)?.let { inspector.discard(it.app) }
        step = TrackStep.Enter()
    }

    fun close() {
        work?.cancel()
        (step as? TrackStep.Confirm)?.let { inspector.discard(it.app) }
    }

    // how the confirm card shows the app before it's saved
    fun preview(confirm: TrackStep.Confirm): AppProfile =
        AppProfile(
            id = trackedIdFor(confirm.found.ref),
            displayName = confirm.app.label,
            packageName = confirm.app.packageName,
            supportStatus = dev.cl0ud9.krate.domain.model.SupportStatus.SUPPORTED,
            installationMode = dev.cl0ud9.krate.domain.model.InstallationMode.UPDATE,
            dependencyIds = emptyList(),
            releaseNotes = null,
            enabled = true,
            artifacts = emptyList(),
            iconPng = confirm.app.iconPng,
        )
}

// why a lookup came to nothing, in a sentence
private fun missedLookUp(
    result: LookUp,
    ref: RepoRef,
): String =
    when (result) {
        LookUp.NoAppFiles -> "${ref.slug} has no app files for this phone in its releases."
        LookUp.NotFound ->
            "Krate couldn't find ${ref.slug} on ${ref.forge.siteName}. It may be private, or spelt differently."
        is LookUp.OutOfChecks -> outOfChecks(result.withToken, ref.forge)
        else -> "Krate couldn't reach ${ref.forge.siteName}. Check your connection and try again."
    }

// a spinner shows at least this long, so a quick answer never reads as a flicker
private const val MIN_SPINNER_MS = 700L

// with playful messages on, a line in front of the fact; just the fact with them off
private fun deadEnd(fact: String): String = KrateVoice.leadIn(Moment.TRACK_DEAD_END, fact)

private fun alreadyHere(fact: String): String = KrateVoice.leadIn(Moment.TRACK_ALREADY, fact)

private fun outOfChecks(
    withToken: Boolean,
    forge: Forge,
): String =
    when {
        forge != Forge.GITHUB -> "${forge.siteName} is limiting checks right now. Try again in a little while."
        withToken -> "GitHub has had a lot of checks from you this hour. Try again in a little while."
        else ->
            "GitHub only allows a few checks an hour without a token. Add one in Settings > GitHub access, or try " +
                "again later."
    }
