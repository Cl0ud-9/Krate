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
        // the user declining the system confirmation dialog is an expected outcome, not a technical
        // failure - PackageInstaller's own EXTRA_STATUS_MESSAGE for this case is raw internal text
        // (e.g. "INSTALL_FAILED_ABORTED: User rejected permission"), never fit to show verbatim
        PackageInstaller.STATUS_FAILURE_ABORTED ->
            InstallStatus.Failed(
                when (waitingForUserStep) {
                    WaitingForUserStep.UNINSTALL_CONFIRM -> "Uninstall cancelled."
                    WaitingForUserStep.INSTALL_CONFIRM -> "Installation cancelled."
                },
                userCancelled = true,
            )

        else -> InstallStatus.Failed(friendlyFailure(message))
    }

// the two failures a user can actually act on get plain wording - both are fixed by the clean install
// App Details offers next to them. Anything else keeps Android's own message
private fun friendlyFailure(message: String?): String =
    when {
        message == null -> "Installation failed."
        "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in message ->
            "The installed app is signed with a different key, so it can't be updated in place. " +
                "A clean install replaces it."
        "INSTALL_FAILED_VERSION_DOWNGRADE" in message ->
            "The installed version is newer, so Android won't install this one over it. A clean install replaces it."
        else -> message
    }
