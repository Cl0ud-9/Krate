package dev.cl0ud9.manager.platform.packageinstaller

import android.content.pm.PackageInstaller
import dev.cl0ud9.manager.domain.model.InstallStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// section 15 of the spec: pending user action must map to an explicit resumable state, not a failure
class InstallResultMappingTest {
    @Test
    fun `success status maps to Success`() {
        val status = interpretInstallResult(PackageInstaller.STATUS_SUCCESS, message = null)
        assertEquals(InstallStatus.Success, status)
    }

    @Test
    fun `pending user action maps to WaitingForUser, not a failure`() {
        val status = interpretInstallResult(PackageInstaller.STATUS_PENDING_USER_ACTION, message = null)
        assertEquals(InstallStatus.WaitingForUser, status)
    }

    @Test
    fun `failure status maps to Failed with the system message`() {
        val status = interpretInstallResult(PackageInstaller.STATUS_FAILURE_INVALID, "bad apk")
        assertTrue(status is InstallStatus.Failed)
        assertEquals("bad apk", (status as InstallStatus.Failed).reason)
    }

    @Test
    fun `failure status with no message falls back to a generic reason`() {
        val status = interpretInstallResult(PackageInstaller.STATUS_FAILURE, message = null)
        assertTrue(status is InstallStatus.Failed)
        assertEquals("Installation failed.", (status as InstallStatus.Failed).reason)
    }
}
