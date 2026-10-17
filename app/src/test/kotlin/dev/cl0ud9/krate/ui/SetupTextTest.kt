package dev.cl0ud9.krate.ui

import dev.cl0ud9.krate.platform.setup.SetupApplied
import dev.cl0ud9.krate.ui.settings.appliedText
import dev.cl0ud9.krate.ui.settings.loadSummary
import dev.cl0ud9.krate.ui.settings.namesText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupTextTest {
    @Test
    fun `names read like a sentence, and a long list is cut short`() {
        assertEquals("A", namesText(listOf("A")))
        assertEquals("A and B", namesText(listOf("A", "B")))
        assertEquals("A, B and C", namesText(listOf("A", "B", "C")))
        assertEquals("A, B, C and D", namesText(listOf("A", "B", "C", "D")))
        assertEquals("A, B, C and 3 more", namesText(listOf("A", "B", "C", "D", "E", "F")))
    }

    @Test
    fun `the summary says each thing on its own line and what stays`() {
        val text = loadSummary(listOf("Demo App"), manual = 2, alreadyHere = 1, unusable = 3)

        assertTrue(text.contains("• start tracking Demo App"))
        assertTrue(text.contains("• keep 2 apps on manual updates"))
        assertTrue(text.contains("1 is already here and stays as it is."))
        assertTrue(text.contains("3 entries in the file couldn't be used."))
    }

    @Test
    fun `nothing about tracking or manual updates when the file has none`() {
        val text = loadSummary(emptyList(), manual = 0, alreadyHere = 0, unusable = 0)

        assertFalse(text.contains("tracking"))
        assertFalse(text.contains("manual"))
        assertTrue(text.contains("look and download choices"))
    }

    @Test
    fun `the result counts what was added and what was already here`() {
        val text = appliedText(SetupApplied(trackedAdded = 2, trackedSkipped = 1, manualSet = 0, settingsSet = 5), 1)

        assertEquals("Loaded: now tracking 2 apps, 1 already here, 1 couldn't be used, 5 choices set.", text)
    }
}
