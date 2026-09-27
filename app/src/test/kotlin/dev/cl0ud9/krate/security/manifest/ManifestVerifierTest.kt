package dev.cl0ud9.krate.security.manifest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

// test vectors generated locally with:
//   openssl genpkey -algorithm ed25519 -out k.key
//   openssl pkey -in k.key -pubout -out k.pub
//   openssl pkeyutl -sign -inkey k.key -rawin -in test-manifest.json -out test-manifest.json.sig
// proves an openssl-signed manifest actually verifies through Tink's Ed25519Verify, not just in theory
class ManifestVerifierTest {
    private val testPublicKeyBase64 = "Whz/VtPy4gEl4y12za+xkWCpfQd3sCE4SFBb6U/bLNE="
    private val testManifestBytes = "{\"schemaVersion\":1,\"apps\":[]}".toByteArray()
    private val testSignatureBase64 =
        "Jf7IymxpcVC1FkAi3JYwgAO2wqicvfKmgl2AMdZp5sHoMC5IuEU6QvX8ZRzLsfv27DMtudgkDoqt75jxkTmdCg=="

    // signed with the private catalog's real key (openssl pkeyutl -sign -rawin), checked against the key the app ships
    @Test
    fun `the private catalog's key verifies its own signatures`() {
        val verifier = ManifestVerifier(dev.cl0ud9.krate.data.catalog.PRIVATE_CATALOG_PUBLIC_KEY_BASE64)
        val signed = "krate private catalog test vector".toByteArray()
        val signature =
            Base64.getDecoder().decode(
                "Hv8ZKsqdkKSSJv2a7C0Wcw+l72lZkLYx9W2OqZFClJ2z+J2Hx3KC6FTvfY16IbLoBeaw56ZT85lJsnxS0x9bAQ==",
            )

        assertTrue(verifier.verify(signed, signature))
        assertFalse(verifier.verify("something else".toByteArray(), signature))
    }

    @Test
    fun `verifies a real openssl-signed manifest`() {
        val verifier = ManifestVerifier(testPublicKeyBase64)
        val signature = Base64.getDecoder().decode(testSignatureBase64)

        assertTrue(verifier.verify(testManifestBytes, signature))
    }

    @Test
    fun `rejects a signature for different content`() {
        val verifier = ManifestVerifier(testPublicKeyBase64)
        val signature = Base64.getDecoder().decode(testSignatureBase64)
        val tamperedManifest = "{\"schemaVersion\":1,\"apps\":[{}]}".toByteArray()

        assertFalse(verifier.verify(tamperedManifest, signature))
    }

    @Test
    fun `rejects a signature from the wrong key`() {
        // a second, unrelated Ed25519 public key
        val wrongKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        val verifier = ManifestVerifier(wrongKey)
        val signature = Base64.getDecoder().decode(testSignatureBase64)

        assertFalse(verifier.verify(testManifestBytes, signature))
    }
}
