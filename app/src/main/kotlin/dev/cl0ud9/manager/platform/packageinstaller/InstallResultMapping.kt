package dev.cl0ud9.manager.platform.packageinstaller

import android.content.pm.PackageInstaller
import dev.cl0ud9.manager.domain.model.InstallStatus

// maps a PackageInstaller broadcast's status extra to our domain state, section 15 of the spec:
// pending user action is an explicit resumable state, never treated as a failure
fun interpretInstallResult(
    status: Int,
    message: String?,
): InstallStatus =
    when (status) {
        PackageInstaller.STATUS_SUCCESS -> InstallStatus.Success
        PackageInstaller.STATUS_PENDING_USER_ACTION -> InstallStatus.WaitingForUser
        else -> InstallStatus.Failed(message ?: "Installation failed.")
    }
