package dev.cl0ud9.manager.security.apk

// package identity + signing certificate digest read back out of a downloaded apk before install
data class ApkArchiveInfo(
    val packageName: String,
    val certificateSha256Hex: String,
)

interface ApkArchiveReader {
    fun read(apkPath: String): ApkArchiveInfo?
}
