package dev.cl0ud9.manager.domain.updateall

import dev.cl0ud9.manager.data.downloads.ArtifactDownloader
import dev.cl0ud9.manager.domain.installer.CleanInstallOrchestrator
import dev.cl0ud9.manager.domain.installer.InstallationEngine
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.DownloadStatus
import dev.cl0ud9.manager.domain.model.InstallStatus
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.model.WaitingForUserStep
import dev.cl0ud9.manager.domain.model.latestArtifact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

// sequential, dependency-ordered deployment for every pending update at once, section 23 + 42.21 of
// the spec. Each app goes through exactly the same download -> verify -> install pipeline a single
// update already uses (phase 3/4/5, section 44.1's per-step WAITING_FOR_USER included via
// InstallationEngine/CleanInstallOrchestrator) - this just drives it across an ordered list, keeps a
// running result summary, and does not stop the batch when one app fails.
class UpdateAllEngine(
    private val artifactDownloader: ArtifactDownloader,
    private val installationEngine: InstallationEngine,
    private val cleanInstallOrchestrator: CleanInstallOrchestrator,
) {
    fun run(orderedApps: List<AppProfile>): Flow<UpdateAllProgress> =
        flow {
            val completed = mutableListOf<UpdateAllOutcome>()
            for ((index, app) in orderedApps.withIndex()) {
                val outcome =
                    updateOne(app) { label ->
                        emit(UpdateAllProgress.Step(app, index, orderedApps.size, label, completed.toList()))
                    }
                completed += outcome
            }
            emit(UpdateAllProgress.Finished(completed))
        }

    private suspend fun updateOne(
        app: AppProfile,
        onStatus: suspend (String) -> Unit,
    ): UpdateAllOutcome {
        onStatus("Downloading ${app.displayName}")
        val downloaded =
            downloadToReady(app, onStatus)
                ?: return UpdateAllOutcome(app, succeeded = false, reason = "Download failed")

        val apkFile = File(downloaded.filePath)
        val installFlow =
            if (app.installationMode == InstallationMode.CLEAN_INSTALL) {
                cleanInstallOrchestrator.cleanInstall(app, apkFile)
            } else {
                installationEngine.install(app, apkFile)
            }

        var failure: InstallStatus.Failed? = null
        var succeeded = false
        installFlow.collect { status ->
            onStatus(installStatusLabel(status))
            when (status) {
                is InstallStatus.Success -> succeeded = true
                is InstallStatus.Failed -> failure = status
                else -> Unit
            }
        }

        return if (succeeded) {
            // same reasoning as AppDetailsViewModel: redundant once actually installed, not deleted on
            // failure since the caller may retry against this same downloaded file
            artifactDownloader.deleteDownloadedFile(downloaded.filePath)
            UpdateAllOutcome(app, succeeded = true)
        } else {
            UpdateAllOutcome(app, succeeded = false, reason = failure?.reason ?: "Installation did not complete")
        }
    }

    private suspend fun downloadToReady(
        app: AppProfile,
        onStatus: suspend (String) -> Unit,
    ): DownloadStatus.ReadyToInstall? {
        // Update All always targets the newest version, never an older retained one - that
        // picking is only ever an explicit, single-app choice made from App Details
        val artifact = app.latestArtifact ?: return null
        var result: DownloadStatus.ReadyToInstall? = null
        artifactDownloader.download(app, artifact).collect { status ->
            when (status) {
                is DownloadStatus.Downloading -> onStatus("Downloading ${app.displayName}")
                is DownloadStatus.Verifying -> onStatus("Verifying ${app.displayName}")
                is DownloadStatus.ReadyToInstall -> result = status
                is DownloadStatus.Failed, DownloadStatus.Idle -> Unit
            }
        }
        return result
    }

    private fun installStatusLabel(status: InstallStatus): String =
        when (status) {
            InstallStatus.PreparingRollback -> "Preserving the current version for rollback"
            InstallStatus.Uninstalling -> "Uninstalling the current version"
            InstallStatus.Installing -> "Installing"
            is InstallStatus.WaitingForUser ->
                when (status.step) {
                    WaitingForUserStep.UNINSTALL_CONFIRM -> "Confirm the uninstall in the system dialog"
                    WaitingForUserStep.INSTALL_CONFIRM -> "Confirm the install in the system dialog"
                }
            InstallStatus.RollingBack -> "Install failed, restoring the previous version"
            InstallStatus.Success -> "Installed"
            InstallStatus.Idle -> "Preparing"
            is InstallStatus.Failed -> "Failed: ${status.reason}"
        }
}
