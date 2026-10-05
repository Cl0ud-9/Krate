package dev.cl0ud9.krate.domain.repository

import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.ArtifactInfo
import dev.cl0ud9.krate.domain.version.isNewerVersion
import dev.cl0ud9.krate.platform.packageinfo.InstalledVersion
import kotlinx.coroutines.flow.Flow

// what Krate last installed for one app. buildId is null for anything recorded before builds
// had ids (Krate 0.1.5 and older) and for the fallback guess below
data class Baseline(
    val versionName: String,
    val buildId: String? = null,
    // the theme it was installed from, for apps that publish several side by side
    val label: String? = null,
)

// remembers the build this Krate actually installed for each app, keyed by package name -
// deliberately separate from ActivityLogRepository (capped at a small number of newest entries,
// display-oriented) since this needs to be permanent, one entry per app, and is read to drive real
// update-availability decisions, not just shown to the user
interface KrateBaselineStore {
    fun observeBaselines(): Flow<Map<String, Baseline>>

    suspend fun recordInstall(
        packageName: String,
        artifact: ArtifactInfo,
    )

    // after an uninstall there is nothing installed for a baseline to describe - a later reinstall
    // records a fresh one
    suspend fun clear(packageName: String)
}

// the baseline to actually compare against: the recorded one if Krate has ever completed a
// real install for this app, otherwise the best available guess. A package can arrive already
// diverged - self-updated outside this app entirely (MicroG RE's own in-app "hide icon" toggle
// installs its own beta build, for example) - before this app ever recorded anything for it. If the
// live-installed version is one the catalog recognizes, it's trustworthy on its own; if not, the
// live version is exactly what we don't trust, so the guess comes from the catalog's OWN memory
// instead: the newest build it had already published when the installed one appeared (its
// lastUpdateTime), else the oldest build it still retains. Deliberately not persisted - it's cheap
// to recompute, and persisting it would lock in a guess instead of a fact the moment a real
// install happens and overwrites it
fun effectiveBaseline(
    recordedBaseline: Baseline?,
    app: AppProfile,
    installed: InstalledVersion?,
): Baseline? {
    // removed outside Krate: whatever was recorded no longer describes anything on the phone
    if (installed == null) return null
    val installedVersionName = installed.versionName
    val baseline =
        when {
            recordedBaseline != null -> recordedBaseline
            installedVersionName != null && app.artifacts.any { it.versionName == installedVersionName } ->
                matchingBuild(app, installedVersionName, installed.lastUpdateTimeMillis)
            else -> guessFromCatalog(app, installed.lastUpdateTimeMillis)
        }
    return baseline?.notNewerThan(installedVersionName, app)
}

// only versions that both start with a number are compared; a "v7.1.1" against "7.1.1" would read as older
private fun isClearlyOlder(
    installed: String,
    than: String,
): Boolean =
    installed.firstOrNull()?.isDigit() == true &&
        than.firstOrNull()?.isDigit() == true &&
        isNewerVersion(than, installed)

// never ahead of what's actually installed: an older build put on outside Krate (or one installed before Krate) is
// behind, whatever was recorded or guessed, so the newer builds show as updates. A newer one outside Krate (a beta)
// keeps the baseline as it is, so it isn't nagged about
private fun Baseline.notNewerThan(
    installedVersionName: String?,
    app: AppProfile,
): Baseline =
    if (installedVersionName != null && isClearlyOlder(installedVersionName, versionName)) {
        // the theme it was on stays known, so only that theme's builds count as its updates
        val theme = label ?: buildId?.let { id -> app.artifacts.firstOrNull { it.buildId == id }?.label }
        Baseline(installedVersionName, buildId = null, label = theme)
    } else {
        this
    }

// the installed version is one the catalog knows, but several builds can share a version (rebuilds
// on the same version) - the one it is, is the newest of them already published when it was
// installed. Without publish dates there's no telling, so the build stays unknown
private fun matchingBuild(
    app: AppProfile,
    versionName: String,
    installedAtMillis: Long,
): Baseline {
    val build =
        app.artifacts.firstOrNull { artifact ->
            val published = artifact.publishedAtMillis
            artifact.versionName == versionName &&
                published != null &&
                installedAtMillis > 0 &&
                published <= installedAtMillis
        }
    return Baseline(versionName, build?.buildId)
}

private fun guessFromCatalog(
    app: AppProfile,
    installedAtMillis: Long,
): Baseline? {
    val publishedBefore =
        app.artifacts.firstOrNull { artifact ->
            val published = artifact.publishedAtMillis
            published != null && installedAtMillis > 0 && published <= installedAtMillis
        }
    return (publishedBefore ?: app.artifacts.lastOrNull())?.let { Baseline(it.versionName, it.buildId) }
}

// true when this artifact is something newer than the baseline. With build ids on both sides the
// catalog's own order decides - it lists builds newest release first, and for apps Krate builds
// itself a newer build is newer even on the same (or an older) app version. A baseline build missing from the list
// was pruned long ago, so it is older. Without build ids it falls back to the version number
fun ArtifactInfo.isNewerThan(
    baseline: Baseline,
    artifacts: List<ArtifactInfo>,
): Boolean {
    val baselineBuildId = baseline.buildId
    return when {
        buildId != null && baselineBuildId != null -> isListedBefore(baselineBuildId, baseline, artifacts)
        isNewerVersion(versionName, baseline.versionName) -> true
        versionName != baseline.versionName -> false
        // a pre-build-id record of a privately built app (rebuilt on the same version) can't be matched
        // to a build, so it is treated as older. Public apps publish one
        // build per version, so their same-version record is simply that build
        else -> baselineBuildId == null && buildId != null && requiresAuth
    }
}

// themes of one app published side by side (same app, different label) are separate tracks: once one is
// installed, its updates, its "latest" and every comparison only look at builds of that theme
fun AppProfile.forTrack(baseline: Baseline?): AppProfile {
    val label = baseline?.label ?: baseline?.buildId?.let { id -> artifacts.firstOrNull { it.buildId == id }?.label }
    val track = label?.let { theme -> artifacts.filter { it.label == theme } }.orEmpty()
    return if (track.isEmpty() || track.size == artifacts.size) this else copy(artifacts = track)
}

private fun ArtifactInfo.isListedBefore(
    baselineBuildId: String,
    baseline: Baseline,
    artifacts: List<ArtifactInfo>,
): Boolean {
    val baselineIndex = artifacts.indexOfFirst { it.buildId == baselineBuildId }
    return when {
        buildId == baselineBuildId -> false
        baselineIndex == -1 -> !isNewerVersion(baseline.versionName, versionName)
        else -> baselineIndex > artifacts.indexOf(this)
    }
}
