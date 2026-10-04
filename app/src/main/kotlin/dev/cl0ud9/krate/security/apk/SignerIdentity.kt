package dev.cl0ud9.krate.security.apk

import android.content.pm.SigningInfo
import dev.cl0ud9.krate.security.hash.sha256Hex

// the certificate an app is identified by: its first signer, before any key rotation (v1 has no rotation), the
// same digest the catalog lists for each build
fun SigningInfo.identitySha256(): String? {
    val signers = if (hasMultipleSigners()) apkContentsSigners else signingCertificateHistory
    return signers?.firstOrNull()?.let { sha256Hex(it.toByteArray().inputStream()) }
}
