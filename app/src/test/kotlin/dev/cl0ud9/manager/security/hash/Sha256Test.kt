package dev.cl0ud9.manager.security.hash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

// hashes below are real openssl-produced sha256 test vectors, not fabricated
class Sha256Test {
    @Test
    fun `hashes an empty stream to the known empty digest`() {
        val hash = sha256Hex(ByteArrayInputStream(ByteArray(0)))
        assertEquals(EMPTY_SHA256, hash)
    }

    @Test
    fun `hashes known bytes to the known digest`() {
        val hash = sha256Hex(ByteArrayInputStream("hello world".toByteArray()))
        assertEquals(HELLO_WORLD_SHA256, hash)
    }

    @Test
    fun `hashesMatch ignores case`() {
        assertTrue(hashesMatch("AABBCC", "aabbcc"))
    }

    @Test
    fun `hashesMatch rejects a real mismatch`() {
        assertFalse(hashesMatch("aabbcc", "aabbcd"))
    }

    private companion object {
        const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        const val HELLO_WORLD_SHA256 = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
    }
}
