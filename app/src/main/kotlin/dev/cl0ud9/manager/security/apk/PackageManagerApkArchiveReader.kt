package dev.cl0ud9.manager.security.apk

import android.content.Context
import android.content.pm.PackageManager
import dev.cl0ud9.manager.security.hash.sha256Hex

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
        val signingInfo = info?.signingInfo
        // v1 does not support signer rotation, the first signer is the identity compared against the manifest
        val signers =
            signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        val firstSigner = signers?.firstOrNull()
        if (packageName == null || firstSigner == null) return null
        val certificateSha256Hex = sha256Hex(firstSigner.toByteArray().inputStream())
        return ApkArchiveInfo(packageName = packageName, certificateSha256Hex = certificateSha256Hex)
    }
}
