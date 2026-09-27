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
    val installedVersionName = installed?.versionName
    return when {
        recordedBaseline != null -> recordedBaseline
        installed == null -> null
        installedVersionName != null && app.artifacts.any { it.versionName == installedVersionName } ->
            matchingBuild(app, installedVersionName, installed.lastUpdateTimeMillis)
        else -> guessFromCatalog(app, installed.lastUpdateTimeMillis)
    }
}

// the installed version is one the catalog knows, but several builds can share a version (patched
// rebuilds with newer patches) - the one it is, is the newest of them already published when it was
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
// catalog's own order decides - it lists builds newest release first, and for patched apps a
// release is a patches release, so a new patches release is newer even on the same (or, if the
// patches dropped support for it, an older) app version. A baseline build missing from the list
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
        // a pre-build-id record of a privately built app (patched ones, rebuilt whenever patches
        // change) can't be matched to a build, so it is treated as older. Public apps publish one
        // build per version, so their same-version record is simply that build
        else -> baselineBuildId == null && buildId != null && requiresAuth
    }
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
