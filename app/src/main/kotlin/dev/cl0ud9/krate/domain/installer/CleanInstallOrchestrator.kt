package dev.cl0ud9.krate.domain.installer

import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.platform.rollback.RollbackStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

// preserve -> uninstall -> install, restoring the preserved apk if the new install fails,
// section 17, 18, 21, 42.12, 42.13 of the spec. used for apps that always clean install, and
// as the explicit, user-confirmed fallback when a normal in-place update fails
class CleanInstallOrchestrator(
    private val installationEngine: InstallationEngine,
    private val rollbackStore: RollbackStore,
) {
    fun cleanInstall(
        app: AppProfile,
        apkFile: File,
    ): Flow<InstallStatus> =
        flow {
            emit(InstallStatus.PreparingRollback)
            val rollbackCaptured = rollbackStore.capture(app.packageName)

            emit(InstallStatus.Uninstalling)
            var uninstallFailure: InstallStatus.Failed? = null
            installationEngine.uninstall(app.packageName).collect { status ->
                when (status) {
                    is InstallStatus.Failed -> uninstallFailure = status
                    is InstallStatus.WaitingForUser -> emit(status)
                    else -> Unit
                }
            }
            val uninstallError = uninstallFailure
            if (uninstallError != null) {
                emit(InstallStatus.Failed(uninstallError.reason))
                return@flow
            }

            var installFailure: InstallStatus.Failed? = null
            installationEngine.install(app, apkFile).collect { status ->
                if (status is InstallStatus.Failed) installFailure = status else emit(status)
            }
            val failure = installFailure
            if (failure == null) {
                // the new build is on; the copy of the old one has done its job (it can be over 100 MB)
                rollbackStore.discard(app.packageName)
                return@flow
            }
            val outcome = attemptRollback(app, failure, rollbackCaptured)
            // restored, so the old app is back on the phone; when even that failed the copy stays, the last one left
            if (outcome.rolledBack) rollbackStore.discard(app.packageName)
            emit(outcome)
        }

    private suspend fun attemptRollback(
        app: AppProfile,
        failure: InstallStatus.Failed,
        rollbackCaptured: Boolean,
    ): InstallStatus.Failed {
        val rollbackFile = if (rollbackCaptured) rollbackStore.rollbackFile(app.packageName) else null
        if (rollbackFile == null) return failure.copy(rolledBack = false)

        var rollbackFailed = false
        installationEngine.install(app, rollbackFile).collect { status ->
            if (status is InstallStatus.Failed) rollbackFailed = true
        }
        return failure.copy(rolledBack = !rollbackFailed)
    }
}
