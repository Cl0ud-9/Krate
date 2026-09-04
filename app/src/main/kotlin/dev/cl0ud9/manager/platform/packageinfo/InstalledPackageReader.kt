package dev.cl0ud9.manager.platform.packageinfo

// the installed version of a package on this device, section 13 + 42.19 of the spec use this to
// decide Install vs Update wording and to compute real pending-update counts
data class InstalledVersion(
    val versionName: String?,
    val versionCode: Long,
)

interface InstalledPackageReader {
    fun installedVersion(packageName: String): InstalledVersion?
}

// true when the catalog's latest version genuinely differs from what's installed - string comparison
// since the manifest does not yet expose versionCode to the client, section 42.19
fun isUpdateAvailable(
    installed: InstalledVersion?,
    latestVersionName: String?,
): Boolean = installed != null && latestVersionName != null && installed.versionName != latestVersionName
