package dev.cl0ud9.krate.ui

import dev.cl0ud9.krate.ui.details.fingerprintFull
import dev.cl0ud9.krate.ui.details.fingerprintStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignerLineTest {
    private val hex = "3fa29c41b7d0e85a6c1122334455667788990011aabbccddeeff00112233aabb"

    @Test
    fun `the start of a fingerprint is capitals in pairs with an ellipsis`() {
        assertEquals("3F:A2:9C:41:B7:D0:E8:5A...", fingerprintStart(hex))
    }

    @Test
    fun `a fingerprint with colons or spaces reads the same`() {
        assertEquals(fingerprintStart(hex), fingerprintStart(fingerprintFull(hex)))
        assertEquals(fingerprintStart(hex), fingerprintStart(" $hex "))
    }

    @Test
    fun `the whole fingerprint is pairs of capitals`() {
        assertEquals(
            "3F:A2:9C:41:B7:D0:E8:5A:6C:11:22:33:44:55:66:77:88:99:00:11:AA:BB:CC:DD:EE:FF:00:11:22:33:AA:BB",
            fingerprintFull(hex),
        )
    }

    @Test
    fun `nothing that isn't a fingerprint is shown as one`() {
        assertNull(fingerprintStart(null))
        assertNull(fingerprintStart(""))
        assertNull(fingerprintStart("not a fingerprint at all, honestly"))
        assertNull(fingerprintStart("3fa2"))
        assertNull(fingerprintStart("3fa29c41b7d0e85g"))
    }
}
