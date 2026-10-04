package dev.cl0ud9.krate.platform.packageinfo

import android.content.Context
import android.content.pm.PackageManager
import dev.cl0ud9.krate.security.apk.identitySha256

// reads the installed version of a catalog app from the device's package manager, section 13 of the spec
class PackageManagerInstalledPackageReader(
    private val context: Context,
) : InstalledPackageReader {
    override fun installedVersion(packageName: String): InstalledVersion? =
        runCatching { context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES) }
            .getOrNull()
            ?.let {
                InstalledVersion(
                    versionName = it.versionName,
                    versionCode = it.longVersionCode,
                    lastUpdateTimeMillis = it.lastUpdateTime,
                    signerSha256 = runCatching { it.signingInfo?.identitySha256() }.getOrNull(),
                )
            }
}
