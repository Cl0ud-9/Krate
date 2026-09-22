package dev.cl0ud9.manager.platform.packageinfo

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

// true when the catalog's latest version is actually NEWER than what's installed, not merely
// different - a plain != used to flag "update available" for a package that self-updated to
// something ahead of the catalog outside this app entirely (confirmed live: MicroG RE's own
// in-app "hide icon" toggle installs its own beta build), which would have offered a downgrade
// back to the catalog's older release. Falls back to "available" when the installed versionName
// couldn't be read at all - something is installed and the catalog has a version, just nothing
// to compare, so nudging toward reinstall/update is safer than silently hiding that state
fun isUpdateAvailable(
    installed: InstalledVersion?,
    latestVersionName: String?,
): Boolean {
    val installedVersionName = installed?.versionName
    return when {
        installed == null || latestVersionName == null -> false
        installedVersionName == null -> true
        else -> isNewerVersion(latestVersionName, installedVersionName)
    }
}
