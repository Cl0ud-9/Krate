package dev.cl0ud9.krate.platform.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SameSettingsTextTest {
    @Test
    fun `spacing and line breaks don't matter`() {
        assertTrue(sameSettingsText("\"a\": 1,\n\"b\": 2", "\"a\":1, \"b\":2"))
    }

    @Test
    fun `a box that kept its old text is not what was pasted`() {
        assertFalse(sameSettingsText("\"a\": 1", "\"a\": 1,\n\"b\": 2"))
    }

    @Test
    fun `an empty box is never what was pasted`() {
        assertFalse(sameSettingsText("", "\"a\": 1"))
    }
}
