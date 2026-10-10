package dev.cl0ud9.krate.data.tracked

import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.ArtifactInfo
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.model.SupportStatus
import dev.cl0ud9.krate.domain.tracked.Forge
import dev.cl0ud9.krate.domain.tracked.RepoRef
import dev.cl0ud9.krate.domain.tracked.pickAsset
import dev.cl0ud9.krate.domain.tracked.versionFromTag
import kotlinx.serialization.Serializable

// how many builds each tracked app keeps in its version history
private const val KEPT_BUILDS = 5
private const val ID_PREFIX = "tracked-"
private val UNSAFE_ID_CHARS = Regex("[^a-z0-9._-]")

// an app someone follows from a repository themselves, outside the catalog: on GitHub, Codeberg or GitLab
@Serializable
data class TrackedEntry(
    val id: String,
    val owner: String,
    val repo: String,
    val packageName: String,
    val displayName: String,
    val description: String? = null,
    // the app's own icon, read from its file when it was added
    val iconPng: String? = null,
    // the developer's signing certificate when it was added; every later download must carry the same one
    val certificateSha256: String,
    // the shape of the file picked from each release (see nameShape)
    val fileShape: String,
    val includePrerelease: Boolean = false,
    val addedAtMillis: Long,
    // newest first
    val builds: List<TrackedBuild> = emptyList(),
    // the site's tag for the last list of releases, so an unchanged list costs nothing to check again
    val etag: String? = null,
    // where it lives; entries saved before other sites were followed are GitHub ones
    val forge: String = Forge.GITHUB.id,
) {
    val ref: RepoRef get() = RepoRef(owner, repo, Forge.fromId(forge))
}

@Serializable
data class TrackedBuild(
    val tag: String,
    val fileName: String,
    val downloadUrl: String,
    val sha256: String? = null,
    val notes: String? = null,
    val publishedAtMillis: Long? = null,
)

// a tracked app's id never clashes with a catalog one, whose ids don't start this way; GitHub ones keep the shape they
// always had, and other sites name themselves so the same owner/repo on two sites stays two apps
fun trackedIdFor(ref: RepoRef): String {
    val site = if (ref.forge == Forge.GITHUB) "" else "${ref.forge.id}-"
    return ID_PREFIX + site + "${ref.owner}-${ref.repo}".lowercase().replace(UNSAFE_ID_CHARS, "_")
}

// the builds worth offering from a list of releases: the file shaped like the one picked, from each release that
// has one, pre-releases only when asked for, newest first
fun buildsFrom(
    releases: List<Release>,
    fileShape: String,
    includePrerelease: Boolean,
    supportedAbis: List<String>,
): List<TrackedBuild> =
    releases
        .asSequence()
        .filter { includePrerelease || !it.prerelease }
        .mapNotNull { release ->
            pickAsset(release.assets, fileShape, supportedAbis)?.let { asset ->
                TrackedBuild(
                    tag = release.tag,
                    fileName = asset.name,
                    downloadUrl = asset.downloadUrl,
                    sha256 = asset.sha256,
                    notes = release.notes,
                    publishedAtMillis = release.publishedAtMillis,
                )
            }
        }.take(KEPT_BUILDS)
        .toList()

// a tracked app as the rest of Krate sees it, so updates, notifications and installs work as for any other app. A
// build its site published no digest for is checked by its signature alone
fun TrackedEntry.toProfile(): AppProfile =
    AppProfile(
        id = id,
        displayName = displayName,
        packageName = packageName,
        supportStatus = SupportStatus.SUPPORTED,
        installationMode = InstallationMode.UPDATE,
        dependencyIds = emptyList(),
        releaseNotes = null,
        enabled = true,
        artifacts =
            builds.map { build ->
                ArtifactInfo(
                    versionName = versionFromTag(build.tag),
                    downloadUrl = build.downloadUrl,
                    sha256 = build.sha256.orEmpty(),
                    certificateSha256 = certificateSha256,
                    buildId = build.tag,
                    releaseNotes = build.notes,
                    publishedAtMillis = build.publishedAtMillis,
                )
            },
        iconPng = iconPng,
        description = description,
        trackedRepo = ref.slug,
        trackedForge = forge,
        trackedPrerelease = includePrerelease,
    )
