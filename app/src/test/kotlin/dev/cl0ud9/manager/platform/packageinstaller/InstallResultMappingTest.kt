package dev.cl0ud9.manager.platform.packageinstaller

import android.content.pm.PackageInstaller
import dev.cl0ud9.manager.domain.model.InstallStatus
import dev.cl0ud9.manager.domain.model.WaitingForUserStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// section 15 of the spec: pending user action must map to an explicit resumable state, not a failure.
// amendment 44.1: that resumable state must carry which of the two clean-install dialogs it's for
class InstallResultMappingTest {
    @Test
    fun `success status maps to Success`() {
        val status =
            interpretInstallResult(PackageInstaller.STATUS_SUCCESS, message = null, WaitingForUserStep.INSTALL_CONFIRM)
        assertEquals(InstallStatus.Success, status)
    }

    @Test
    fun `pending user action during an install maps to WaitingForUser with INSTALL_CONFIRM`() {
        val status =
            interpretInstallResult(
                PackageInstaller.STATUS_PENDING_USER_ACTION,
                message = null,
                WaitingForUserStep.INSTALL_CONFIRM,
            )
        assertEquals(InstallStatus.WaitingForUser(WaitingForUserStep.INSTALL_CONFIRM), status)
    }

    @Test
    fun `pending user action during an uninstall maps to WaitingForUser with UNINSTALL_CONFIRM`() {
        val status =
            interpretInstallResult(
                PackageInstaller.STATUS_PENDING_USER_ACTION,
                message = null,
                WaitingForUserStep.UNINSTALL_CONFIRM,
            )
        assertEquals(InstallStatus.WaitingForUser(WaitingForUserStep.UNINSTALL_CONFIRM), status)
    }

    @Test
    fun `user cancelling an install shows a clean cancellation message, not the raw system text`() {
        val status =
            interpretInstallResult(
                PackageInstaller.STATUS_FAILURE_ABORTED,
                "INSTALL_FAILED_ABORTED: User rejected permission",
                WaitingForUserStep.INSTALL_CONFIRM,
            )
        assertEquals(InstallStatus.Failed("Installation cancelled."), status)
    }

    @Test
    fun `user cancelling an uninstall shows a clean, step-specific cancellation message`() {
        val status =
            interpretInstallResult(
                PackageInstaller.STATUS_FAILURE_ABORTED,
                "DELETE_FAILED_ABORTED: User rejected permission",
                WaitingForUserStep.UNINSTALL_CONFIRM,
            )
        assertEquals(InstallStatus.Failed("Uninstall cancelled."), status)
    }

    @Test
    fun `failure status maps to Failed with the system message`() {
        val status =
            interpretInstallResult(
                PackageInstaller.STATUS_FAILURE_INVALID,
                "bad apk",
                WaitingForUserStep.INSTALL_CONFIRM,
            )
        assertTrue(status is InstallStatus.Failed)
        assertEquals("bad apk", (status as InstallStatus.Failed).reason)
    }

    @Test
    fun `failure status with no message falls back to a generic reason`() {
        val status =
            interpretInstallResult(PackageInstaller.STATUS_FAILURE, message = null, WaitingForUserStep.INSTALL_CONFIRM)
        assertTrue(status is InstallStatus.Failed)
        assertEquals("Installation failed.", (status as InstallStatus.Failed).reason)
    }
}
