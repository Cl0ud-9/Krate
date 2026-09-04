package dev.cl0ud9.manager.platform.packageinfo

import android.content.Context

// reads the installed version of a catalog app from the device's package manager, section 13 of the spec
class PackageManagerInstalledPackageReader(
    private val context: Context,
) : InstalledPackageReader {
    override fun installedVersion(packageName: String): InstalledVersion? =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }
            .getOrNull()
            ?.let { InstalledVersion(versionName = it.versionName, versionCode = it.longVersionCode) }
}
