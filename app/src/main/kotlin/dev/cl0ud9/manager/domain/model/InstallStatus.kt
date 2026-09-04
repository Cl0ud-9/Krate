package dev.cl0ud9.manager.domain.model

// installation pipeline states, phase 4 of the spec - section 15 models user action as a state, not a failure
sealed interface InstallStatus {
    data object Idle : InstallStatus

    data object Installing : InstallStatus

    data object WaitingForUser : InstallStatus

    data object Success : InstallStatus

    data class Failed(
        val reason: String,
    ) : InstallStatus
}
