package dev.cl0ud9.manager.domain.model

// installation pipeline states, phase 4 + 5 of the spec - section 15 models user action as a state,
// not a failure; the clean-install states (section 17, 42.13) cover uninstall/reinstall + rollback
sealed interface InstallStatus {
    data object Idle : InstallStatus

    data object PreparingRollback : InstallStatus

    data object Uninstalling : InstallStatus

    data object Installing : InstallStatus

    data object WaitingForUser : InstallStatus

    data object RollingBack : InstallStatus

    data object Success : InstallStatus

    data class Failed(
        val reason: String,
        // whether a rollback to the previous apk was attempted and succeeded after this failure
        val rolledBack: Boolean = false,
    ) : InstallStatus
}
