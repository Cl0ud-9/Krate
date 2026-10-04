package dev.cl0ud9.krate.platform.packageinstaller

import android.content.pm.PackageInstaller
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.domain.model.WaitingForUserStep
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
        assertEquals(InstallStatus.Failed("Installation cancelled.", userCancelled = true), status)
    }

    @Test
    fun `user cancelling an uninstall shows a clean, step-specific cancellation message`() {
        val status =
            interpretInstallResult(
                PackageInstaller.STATUS_FAILURE_ABORTED,
                "DELETE_FAILED_ABORTED: User rejected permission",
                WaitingForUserStep.UNINSTALL_CONFIRM,
            )
        assertEquals(InstallStatus.Failed("Uninstall cancelled.", userCancelled = true, mayBeAppLock = true), status)
    }

    // a phone's own app lock gives no code of its own, so only failures without a clear cause point to it
    @Test
    fun `only failures without a clear cause mention an app lock`() {
        fun failed(
            status: Int,
            message: String?,
        ) = interpretInstallResult(status, message, WaitingForUserStep.INSTALL_CONFIRM) as InstallStatus.Failed

        assertTrue(failed(PackageInstaller.STATUS_FAILURE, "INSTALL_FAILED_INTERNAL_ERROR").mayBeAppLock)
        assertTrue(failed(PackageInstaller.STATUS_FAILURE, message = null).mayBeAppLock)
        assertTrue(!failed(PackageInstaller.STATUS_FAILURE_ABORTED, message = null).mayBeAppLock)
        assertTrue(!failed(PackageInstaller.STATUS_FAILURE_STORAGE, "INSTALL_FAILED_INSUFFICIENT_STORAGE").mayBeAppLock)
        assertTrue(
            !failed(
                PackageInstaller.STATUS_FAILURE_CONFLICT,
                "INSTALL_FAILED_UPDATE_INCOMPATIBLE: mismatch",
            ).mayBeAppLock,
        )
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

    // seen live: an app installed from elsewhere, signed with a different key
    @Test
    fun `a signing key mismatch gets a plain explanation instead of the raw system text`() {
        val status =
            interpretInstallResult(
                PackageInstaller.STATUS_FAILURE_CONFLICT,
                "INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.example.player signatures " +
                    "do not match newer version; ignoring!",
                WaitingForUserStep.INSTALL_CONFIRM,
            )
        val reason = (status as InstallStatus.Failed).reason
        assertTrue(reason.startsWith("The installed app is signed with a different key"))
    }

    @Test
    fun `a downgrade gets a plain explanation instead of the raw system text`() {
        val status =
            interpretInstallResult(
                PackageInstaller.STATUS_FAILURE_CONFLICT,
                "INSTALL_FAILED_VERSION_DOWNGRADE: Downgrade detected: Update version code 108 is " +
                    "older than current 109",
                WaitingForUserStep.INSTALL_CONFIRM,
            )
        val reason = (status as InstallStatus.Failed).reason
        assertTrue(reason.startsWith("The installed version is newer"))
    }

    // what Android reported on device when Play Protect rejected Krate's own update: "aborted", like Cancel, with the
    // verification failure only in the message
    @Test
    fun `a Play Protect block is told apart from a cancel and from other failures`() {
        val blocked =
            interpretInstallResult(
                PackageInstaller.STATUS_FAILURE_ABORTED,
                message = "INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed for file:///data/app/vmdl1.tmp",
                WaitingForUserStep.INSTALL_CONFIRM,
            )
        val cancelled =
            interpretInstallResult(
                PackageInstaller.STATUS_FAILURE_ABORTED,
                message = null,
                WaitingForUserStep.INSTALL_CONFIRM,
            )
        val other =
            interpretInstallResult(
                PackageInstaller.STATUS_FAILURE_STORAGE,
                message = "INSTALL_FAILED_INSUFFICIENT_STORAGE",
                WaitingForUserStep.INSTALL_CONFIRM,
            )

        assertTrue((blocked as InstallStatus.Failed).blockedByPlayProtect)
        assertEquals("Google Play Protect stopped this install.", blocked.reason)
        assertTrue(!(other as InstallStatus.Failed).blockedByPlayProtect)
        assertTrue((cancelled as InstallStatus.Failed).userCancelled)
        assertTrue(!cancelled.blockedByPlayProtect)
    }
}
