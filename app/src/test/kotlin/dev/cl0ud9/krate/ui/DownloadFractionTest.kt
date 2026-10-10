package dev.cl0ud9.krate.ui

import dev.cl0ud9.krate.domain.model.DownloadStatus
import dev.cl0ud9.krate.platform.work.AppWorkState
import dev.cl0ud9.krate.ui.components.downloadFraction
import dev.cl0ud9.krate.ui.components.workProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadFractionTest {
    @Test
    fun `a known size gives the share done`() {
        assertEquals(0.25f, downloadFraction(bytes = 25, total = 100)!!, 0.0001f)
    }

    @Test
    fun `before the first bytes the bar is empty, not waiting, so it never looks like it restarted`() {
        assertEquals(0f, downloadFraction(bytes = 0, total = null)!!, 0f)
        assertEquals(0f, downloadFraction(bytes = 0, total = 0)!!, 0f)
    }

    @Test
    fun `only a size that never comes has no share`() {
        assertNull(downloadFraction(bytes = 25, total = null))
    }

    @Test
    fun `a server overshooting its own size never fills past the end`() {
        assertEquals(1f, downloadFraction(bytes = 120, total = 100)!!, 0f)
    }

    @Test
    fun `the Apps row follows the same rule as the app's page`() {
        val starting = AppWorkState(download = DownloadStatus.Downloading(bytesDownloaded = 0, totalBytes = null))
        assertEquals(0f, workProgress(starting)!!, 0f)
    }
}
