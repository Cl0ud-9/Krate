package dev.cl0ud9.krate.security.apk

import android.content.Context
import android.content.pm.PackageManager

// reads package name + signing certificate digest from a downloaded apk before install, section 19 + 42.9
class PackageManagerApkArchiveReader(
    private val context: Context,
) : ApkArchiveReader {
    override fun read(apkPath: String): ApkArchiveInfo? {
        val info =
            runCatching {
                context.packageManager.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNING_CERTIFICATES)
            }.getOrNull()
        val packageName = info?.packageName
        val certificateSha256Hex = info?.signingInfo?.identitySha256()
        if (packageName == null || certificateSha256Hex == null) return null
        return ApkArchiveInfo(packageName = packageName, certificateSha256Hex = certificateSha256Hex)
    }
}
