package dev.cl0ud9.manager.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.data.downloads.ArtifactDownloader
import dev.cl0ud9.manager.domain.dependency.DependencyGraph
import dev.cl0ud9.manager.domain.installer.CleanInstallOrchestrator
import dev.cl0ud9.manager.domain.installer.InstallationEngine
import dev.cl0ud9.manager.domain.model.ActivityAction
import dev.cl0ud9.manager.domain.model.ActivityEntry
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.DownloadStatus
import dev.cl0ud9.manager.domain.model.InstallStatus
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.repository.ActivityLogRepository
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import dev.cl0ud9.manager.platform.packageinfo.InstalledPackageReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class DependencyInfo(
    val app: AppProfile,
    val installed: Boolean,
)

// six collaborators plus the screen's own appId argument - each one is a distinct, already-shared
// singleton from AppContainer (not something to bundle into an artificial "dependencies" wrapper
// purely to dodge this count), so the added activityLogRepository param is a justified exception
@Suppress("LongParameterList")
class AppDetailsViewModel(
    private val catalogRepository: CatalogRepository,
    private val artifactDownloader: ArtifactDownloader,
    private val installationEngine: InstallationEngine,
    private val cleanInstallOrchestrator: CleanInstallOrchestrator,
    private val installedPackageReader: InstalledPackageReader,
    private val activityLogRepository: ActivityLogRepository,
    appId: String,
) : ViewModel() {
    val app: StateFlow<AppProfile?> =
        catalogRepository
            .observeApp(appId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // installed state is device-local, so a resume-triggered refresh() re-checks it - a successful
    // install also refreshes immediately below, section 13 + 42.19 of the spec
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val installedVersionName: StateFlow<String?> =
        combine(app, refreshTrigger.onStart { emit(Unit) }) { profile, _ -> profile }
            .map { profile -> profile?.let { installedPackageReader.installedVersion(it.packageName)?.versionName } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // direct dependencies with real install state, section 14 + 42.11 of the spec - most apps have none
    val dependencies: StateFlow<List<DependencyInfo>> =
        combine(app, catalogRepository.observeApps(), refreshTrigger.onStart { emit(Unit) }) { profile, catalog, _ ->
            profile to catalog
        }.map { (profile, catalog) -> profile?.let { resolveDependencies(it, catalog) } ?: emptyList() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val mutableDownloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = mutableDownloadStatus.asStateFlow()

    private val mutableInstallStatus = MutableStateFlow<InstallStatus>(InstallStatus.Idle)
    val installStatus: StateFlow<InstallStatus> = mutableInstallStatus.asStateFlow()

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

    fun startDownload() {
        val currentApp = app.value ?: return
        if (mutableDownloadStatus.value is DownloadStatus.Downloading ||
            mutableDownloadStatus.value is DownloadStatus.Verifying
        ) {
            return
        }
        viewModelScope.launch {
            artifactDownloader.download(currentApp).collect { status -> mutableDownloadStatus.value = status }
        }
    }

    // videoapp patched (CLEAN_INSTALL) always goes through the orchestrator, section 16, 42.12 of the spec.
    // normal UPDATE apps attempt an in-place install/update first
    fun startInstall() {
        val currentApp = app.value
        val readyStatus = readyDownload()
        if (currentApp == null || readyStatus == null || isBusy()) return
        val apkFile = File(readyStatus.filePath)
        val flow =
            if (currentApp.installationMode == InstallationMode.CLEAN_INSTALL) {
                cleanInstallOrchestrator.cleanInstall(currentApp, apkFile)
            } else {
                installationEngine.install(currentApp, apkFile)
            }
        runInstallFlow(flow, currentApp)
    }

    // explicit, user-confirmed fallback after a normal update failed, section 17 of the spec
    fun retryAsCleanInstall() {
        val currentApp = app.value
        val readyStatus = readyDownload()
        if (currentApp == null || readyStatus == null || isBusy()) return
        runInstallFlow(cleanInstallOrchestrator.cleanInstall(currentApp, File(readyStatus.filePath)), currentApp)
    }

    private fun resolveDependencies(
        profile: AppProfile,
        catalog: List<AppProfile>,
    ): List<DependencyInfo> =
        DependencyGraph.directDependencies(profile, catalog).map { dependency ->
            DependencyInfo(dependency, installedPackageReader.installedVersion(dependency.packageName) != null)
        }

    private fun readyDownload(): DownloadStatus.ReadyToInstall? =
        mutableDownloadStatus.value as? DownloadStatus.ReadyToInstall

    private fun isBusy(): Boolean =
        when (mutableInstallStatus.value) {
            InstallStatus.Installing, InstallStatus.PreparingRollback,
            InstallStatus.Uninstalling, InstallStatus.RollingBack,
            is InstallStatus.WaitingForUser,
            -> true

            else -> false
        }

    private fun runInstallFlow(
        flow: Flow<InstallStatus>,
        targetApp: AppProfile,
    ) {
        // captured before the flow runs, not after: installedVersionName reflects the OLD device
        // state right now, which is exactly what decides whether this is an install or an update
        val wasInstalled = installedVersionName.value != null
        viewModelScope.launch {
            flow.collect { status ->
                mutableInstallStatus.value = status
                if (status is InstallStatus.Success) {
                    // the downloaded apk is redundant once PackageInstaller has actually committed it -
                    // not deleted on failure, since a retry reuses this same file instead of re-downloading
                    readyDownload()?.let { artifactDownloader.deleteDownloadedFile(it.filePath) }
                    recordActivity(targetApp, wasInstalled)
                    refresh()
                }
            }
        }
    }

    private suspend fun recordActivity(
        targetApp: AppProfile,
        wasInstalled: Boolean,
    ) {
        activityLogRepository.record(
            ActivityEntry(
                id = UUID.randomUUID().toString(),
                appId = targetApp.id,
                appName = targetApp.displayName,
                action = if (wasInstalled) ActivityAction.UPDATED else ActivityAction.INSTALLED,
                timestampMillis = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
