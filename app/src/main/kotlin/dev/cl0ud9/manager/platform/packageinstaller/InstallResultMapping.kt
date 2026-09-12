package dev.cl0ud9.manager.platform.packageinstaller

import android.content.pm.PackageInstaller
import dev.cl0ud9.manager.domain.model.InstallStatus
import dev.cl0ud9.manager.domain.model.WaitingForUserStep

// maps a PackageInstaller broadcast's status extra to our domain state, section 15 of the spec:
// pending user action is an explicit resumable state, never treated as a failure. The caller knows
// whether this broadcast belongs to an install or uninstall session (this function has no way to
// tell on its own), so it supplies which WaitingForUserStep to report if that's the outcome -
// amendment 44.1 of the spec.
fun interpretInstallResult(
    status: Int,
    message: String?,
    waitingForUserStep: WaitingForUserStep,
): InstallStatus =
    when (status) {
        PackageInstaller.STATUS_SUCCESS -> InstallStatus.Success
        PackageInstaller.STATUS_PENDING_USER_ACTION -> InstallStatus.WaitingForUser(waitingForUserStep)
        else -> InstallStatus.Failed(message ?: "Installation failed.")
    }
