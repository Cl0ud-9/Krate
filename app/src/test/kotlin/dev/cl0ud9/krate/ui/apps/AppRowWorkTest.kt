package dev.cl0ud9.krate.ui.apps

import dev.cl0ud9.krate.domain.model.DownloadStatus
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.domain.model.WaitingForUserStep
import dev.cl0ud9.krate.platform.work.AppWorkState
import dev.cl0ud9.krate.ui.components.workLine
import dev.cl0ud9.krate.ui.components.workPhase
import dev.cl0ud9.krate.ui.components.workProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppRowWorkTest {
    @Test
    fun `an idle app shows its usual line and no bar`() {
        val idle = AppWorkState()
        assertNull(workLine(idle))
        assertNull(workProgress(idle))
    }

    @Test
    fun `a download of known size shows how far along it is`() {
        val work = AppWorkState(download = DownloadStatus.Downloading(bytesDownloaded = 25, totalBytes = 100))
        assertEquals("Downloading 25%", workLine(work))
        assertEquals(0.25f, workProgress(work)!!, 0.0001f)
    }

    @Test
    fun `a download whose size never comes turns the loader instead of a bar`() {
        val work = AppWorkState(download = DownloadStatus.Downloading(bytesDownloaded = 25, totalBytes = null))
        assertEquals("Downloading...", workLine(work))
        assertEquals(-1f, workProgress(work)!!, 0f)
    }

    @Test
    fun `checking, installing, waiting and uninstalling each say so`() {
        assertEquals("Checking the download...", workLine(AppWorkState(download = DownloadStatus.Verifying)))
        assertEquals("Installing...", workLine(AppWorkState(install = InstallStatus.Installing)))
        assertEquals(
            "Waiting for your OK...",
            workLine(AppWorkState(install = InstallStatus.WaitingForUser(WaitingForUserStep.INSTALL_CONFIRM))),
        )
        assertEquals("Uninstalling...", workLine(AppWorkState(install = InstallStatus.Uninstalling)))
    }

    @Test
    fun `a download counting up stays one phase, so the line doesn't fade every step`() {
        assertEquals(workPhase("Downloading 25%"), workPhase("Downloading 26%"))
        assertEquals(workPhase("Downloading 25%"), workPhase("Downloading..."))
        assertNotEquals(workPhase("Downloading 99%"), workPhase("Checking the download..."))
        assertNotEquals(workPhase("Checking the download..."), workPhase("Installing..."))
        assertNull(workPhase(null))
    }

    @Test
    fun `the install outranks a download left from before it`() {
        val work =
            AppWorkState(
                download = DownloadStatus.Downloading(bytesDownloaded = 100, totalBytes = 100),
                install = InstallStatus.Installing,
            )
        assertEquals("Installing...", workLine(work))
        assertEquals(-1f, workProgress(work)!!, 0f)
    }
}
