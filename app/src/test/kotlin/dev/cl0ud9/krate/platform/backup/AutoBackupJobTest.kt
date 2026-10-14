package dev.cl0ud9.krate.platform.backup

import dev.cl0ud9.krate.domain.model.AutoBackup
import dev.cl0ud9.krate.domain.model.RecommendedSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoBackupJobTest {
    private val spec = AutoBackup(emptyList(), "Cancel", "Import")

    @Test
    fun `applying carries the picks as the text to paste and their revision`() {
        val job = applyJob("pkg", spec, RecommendedSettings("\"a\": 1", listOf("one"), "rev-3"))

        assertEquals(BackupMode.APPLY, job.mode)
        assertEquals("\"a\": 1", job.text)
        assertEquals("rev-3", job.revision)
        assertNull(job.link)
        assertNull(job.confirm)
    }

    @Test
    fun `restoring carries the saved copy as the text to paste`() {
        val job = restoreJob("pkg", spec, "\"b\": 2")

        assertEquals(BackupMode.RESTORE, job.mode)
        assertEquals("\"b\": 2", job.text)
        assertNull(job.link)
        assertNull(job.revision)
    }
}
