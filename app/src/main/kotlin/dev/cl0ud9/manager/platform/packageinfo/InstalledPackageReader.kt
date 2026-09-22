package dev.cl0ud9.manager.platform.packageinfo

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.latestVersionName
import dev.cl0ud9.manager.domain.version.isNewerVersion

// the installed version of a package on this device, section 13 + 42.19 of the spec use this to
// decide Install vs Update wording and to compute real pending-update counts
data class InstalledVersion(
    val versionName: String?,
    val versionCode: Long,
)

interface InstalledPackageReader {
    fun installedVersion(packageName: String): InstalledVersion?
}

// true when either (a) the installed version isn't one this catalog has ever published for this
// app at all, or (b) it is, but the catalog's latest is actually newer than it - not merely
// different. Case (b) alone used to be a plain !=, which flagged "update available" for a package
// that self-updated to something ahead of the catalog outside this app entirely (confirmed live:
// MicroG RE's own in-app "hide icon" toggle installs its own beta build), offering a downgrade
// back to the catalog's older release. Case (a) is a deliberate policy choice, not a safety
// workaround: an installed build the catalog doesn't recognize at all - such as that same beta -
// is always treated as behind, regardless of its own version number, so the user keeps getting
// steered back toward whatever this catalog actually tracks. app.artifacts only retains a rolling
// window of recent releases (see catalog/scripts/generate_manifest.py's retainVersions), not full
// history, but that never produces a false positive here: anything that ages out of the window is
// - by definition - already older than everything still in it, so case (b) would have flagged it
// as needing an update anyway; only a build the catalog genuinely never published (case (a)) can
// disagree with what case (b) alone would have said, and disagreeing correctly is the entire point
fun isUpdateAvailable(
    installed: InstalledVersion?,
    app: AppProfile,
): Boolean {
    val latestVersionName = app.latestVersionName
    return when {
        installed == null || latestVersionName == null -> false
        installed.versionName == null -> true
        app.artifacts.none { it.versionName == installed.versionName } -> true
        else -> isNewerVersion(latestVersionName, installed.versionName)
    }
}
