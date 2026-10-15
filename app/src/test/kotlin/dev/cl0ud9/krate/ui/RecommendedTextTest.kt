package dev.cl0ud9.krate.ui

import dev.cl0ud9.krate.platform.backup.AutoBackupState
import dev.cl0ud9.krate.platform.backup.BackupMode
import dev.cl0ud9.krate.platform.backup.RecommendedChoice
import dev.cl0ud9.krate.ui.details.applyText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendedTextTest {
    private fun done(
        changed: Int,
        reset: Int,
    ) = AutoBackupState.Done("pkg", BackupMode.APPLY, changed = changed, reset = reset)

    @Test
    fun `already exact says so`() {
        assertEquals(
            "The app already has exactly Krate's settings, so nothing needed changing.",
            applyText(done(0, 0), RecommendedChoice.APPLIED),
        )
    }

    @Test
    fun `a change counts how many went back to their default`() {
        val text = applyText(done(3, 2), RecommendedChoice.APPLIED)

        assertTrue(text.startsWith("Done: 3 settings changed. (2 went back to their default.)"))
    }

    @Test
    fun `nothing is said about resets when none happened`() {
        assertTrue(!applyText(done(1, 0), RecommendedChoice.APPLIED).contains("default"))
    }
}
