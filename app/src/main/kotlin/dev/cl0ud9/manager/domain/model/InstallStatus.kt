package dev.cl0ud9.manager.domain.model

// which of clean-install's two separate system confirmation dialogs WaitingForUser refers to,
// amendment 44.1 of the spec - a non-privileged app can never silently uninstall another package,
// so PackageInstaller.uninstall() raises its own confirmation independent of the install confirmation
enum class WaitingForUserStep {
    UNINSTALL_CONFIRM,
    INSTALL_CONFIRM,
}

// installation pipeline states, phase 4 + 5 of the spec - section 15 models user action as a state,
// not a failure; the clean-install states (section 17, 42.13) cover uninstall/reinstall + rollback
sealed interface InstallStatus {
    data object Idle : InstallStatus

    data object PreparingRollback : InstallStatus

    data object Uninstalling : InstallStatus

    data object Installing : InstallStatus

    // carries which step it's for (amendment 44.1) so the UI shows the right copy and resume logic
    // after process death knows which system dialog to re-arm, rather than a bare re-entrant state
    data class WaitingForUser(
        val step: WaitingForUserStep,
    ) : InstallStatus

    data object RollingBack : InstallStatus

    data object Success : InstallStatus

    data class Failed(
        val reason: String,
        // whether a rollback to the previous apk was attempted and succeeded after this failure
        val rolledBack: Boolean = false,
        // the user said no in Android's confirmation dialog - not something that went wrong
        val userCancelled: Boolean = false,
    ) : InstallStatus
}
