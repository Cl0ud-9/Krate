package dev.cl0ud9.manager.security.manifest

import com.google.crypto.tink.subtle.Ed25519Verify
import java.security.GeneralSecurityException
import java.util.Base64

// placeholder until SETUP.md section 3 is done: generate the real key, then derive this value with
// openssl pkey -in manifest-signing.key -pubout -out manifest-signing.pub -outform DER | tail -c 32 | base64
const val MANIFEST_PUBLIC_KEY_BASE64 = "REPLACE_ME_WITH_REAL_MANIFEST_PUBLIC_KEY"

// verifies the detached Ed25519 signature over the manifest bytes, amendment 44.3 of the spec
class ManifestVerifier(
    publicKeyBase64: String = MANIFEST_PUBLIC_KEY_BASE64,
) {
    private val verifier = Ed25519Verify(Base64.getDecoder().decode(publicKeyBase64))

    // an invalid signature is the expected negative outcome here, not a bug to report upstream
    @Suppress("SwallowedException")
    fun verify(
        manifestBytes: ByteArray,
        signatureBytes: ByteArray,
    ): Boolean =
        try {
            verifier.verify(signatureBytes, manifestBytes)
            true
        } catch (ignored: GeneralSecurityException) {
            false
        }
}
