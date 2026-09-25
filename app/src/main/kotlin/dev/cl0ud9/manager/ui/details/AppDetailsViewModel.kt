package dev.cl0ud9.manager.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.data.downloads.ArtifactDownloader
import dev.cl0ud9.manager.data.downloads.DownloadProgressNotifier
import dev.cl0ud9.manager.domain.dependency.DependencyGraph
import dev.cl0ud9.manager.domain.installer.CleanInstallOrchestrator
import dev.cl0ud9.manager.domain.installer.InstallationEngine
import dev.cl0ud9.manager.domain.model.ActivityAction
import dev.cl0ud9.manager.domain.model.ActivityEntry
import dev.cl0ud9.manager.domain.model.AnnouncementItem
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.ArtifactInfo
import dev.cl0ud9.manager.domain.model.DownloadStatus
import dev.cl0ud9.manager.domain.model.InstallStatus
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.model.isActive
import dev.cl0ud9.manager.domain.model.latestArtifact
import dev.cl0ud9.manager.domain.repository.ActivityLogRepository
import dev.cl0ud9.manager.domain.repository.AnnouncementDismissalStore
import dev.cl0ud9.manager.domain.repository.Baseline
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import dev.cl0ud9.manager.domain.repository.ManagerBaselineStore
import dev.cl0ud9.manager.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.manager.platform.packageinfo.InstalledVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class DependencyInfo(
    val app: AppProfile,
    val installed: Boolean,
)

// nine collaborators plus the screen's own appId argument - each one is a distinct, already-shared
// singleton from AppContainer (not something to bundle into an artificial "dependencies" wrapper
// purely to dodge this count). TooManyFunctions is similarly a real but justified count: this is the
// one class that owns every distinct user-facing operation on this screen (refresh, select a
// version, start/retry a download or install, plus the onCleared cleanup that keeps a backgrounded
// download's notification from outliving it) - splitting those apart would scatter one screen's
// state across several classes rather than actually shrinking any of it
@Suppress("LongParameterList", "TooManyFunctions")
class AppDetailsViewModel(
    private val catalogRepository: CatalogRepository,
    private val artifactDownloader: ArtifactDownloader,
    private val installationEngine: InstallationEngine,
    private val cleanInstallOrchestrator: CleanInstallOrchestrator,
    private val installedPackageReader: InstalledPackageReader,
    private val activityLogRepository: ActivityLogRepository,
    private val managerBaselineStore: ManagerBaselineStore,
    private val downloadProgressNotifier: DownloadProgressNotifier,
    private val announcementDismissalStore: AnnouncementDismissalStore,
    private val appId: String,
) : ViewModel() {
    val app: StateFlow<AppProfile?> =
        catalogRepository
            .observeApp(appId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // the build the manager itself last installed for this app, or null if it never has - App
    // Details reads this alongside installedVersion (the live device state) to tell "up to date"
    // from "diverged outside the manager", see AppDetailsUiState.effectiveBaseline
    val managerBaseline: StateFlow<Baseline?> =
        combine(app, managerBaselineStore.observeBaselines()) { profile, baselines ->
            profile?.let { baselines[it.packageName] }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // installed state is device-local, so a resume-triggered refresh() re-checks it - a successful
    // install also refreshes immediately below, section 13 + 42.19 of the spec
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // installedVersion() is a real PackageManager Binder call, not free - flowOn(IO) keeps it off
    // the main thread, which otherwise blocked right during this screen's own enter transition
    // (collapsing header, depth-blur) every single time it opened
    val installedVersion: StateFlow<InstalledVersion?> =
        combine(app, refreshTrigger.onStart { emit(Unit) }) { profile, _ -> profile }
            .map { profile -> profile?.let { installedPackageReader.installedVersion(it.packageName) } }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // notices from the catalog about this app (see catalog/announcements.json)
    val announcements: StateFlow<List<AnnouncementItem>> =
        combine(
            catalogRepository.observeAnnouncements(),
            announcementDismissalStore.observeDismissed(),
            catalogRepository.observeApps(),
        ) { announcements, dismissed, catalog ->
            val now = System.currentTimeMillis()
            announcements
                .filter { appId in it.appIds && it.isActive(now) && it.id !in dismissed }
                .map { announcement ->
                    AnnouncementItem(announcement, catalog.find { it.id == announcement.actionAppId }?.displayName)
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    // direct dependencies with real install state, section 14 + 42.11 of the spec - most apps have
    // none. Same flowOn(IO) reasoning as installedVersion above - resolveDependencies() calls
    // installedPackageReader once per dependency
    val dependencies: StateFlow<List<DependencyInfo>> =
        combine(app, catalogRepository.observeApps(), refreshTrigger.onStart { emit(Unit) }) { profile, catalog, _ ->
            profile to catalog
        }.map { (profile, catalog) -> profile?.let { resolveDependencies(it, catalog) } ?: emptyList() }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val mutableDownloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = mutableDownloadStatus.asStateFlow()

    private val mutableInstallStatus = MutableStateFlow<InstallStatus>(InstallStatus.Idle)
    val installStatus: StateFlow<InstallStatus> = mutableInstallStatus.asStateFlow()

    // null means "no explicit pick yet, use the newest" - only ever non-null once the user taps a
    // specific version in App Details' version history, section 9 of the spec (artifacts retains
    // more than just the latest so a broken newest build still leaves older ones installable)
    private val mutableExplicitArtifact = MutableStateFlow<ArtifactInfo?>(null)
    val selectedArtifact: StateFlow<ArtifactInfo?> =
        combine(app, mutableExplicitArtifact) { profile, explicit -> explicit ?: profile?.latestArtifact }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // a fresh ViewModel (any plain revisit of this screen - navigating away and back mints a new
    // screen-scoped instance every time, not just process death) otherwise starts at Idle even when
    // a verified download for the current app+version is already sitting on disk from earlier,
    // forcing a redundant "Redownload" the user shouldn't have to do. Only ever moves Idle ->
    // ReadyToInstall, never overwrites a real in-flight Downloading/Verifying/Failed status.
    // existingReadyFile() is a real (if small) blocking File.exists() call - withContext(IO) keeps
    // it off the main thread, same reasoning as installedVersion/dependencies above
    init {
        viewModelScope.launch {
            combine(app.filterNotNull(), selectedArtifact.filterNotNull()) { profile, artifact ->
                profile to artifact
            }.collect { (profile, artifact) ->
                if (mutableDownloadStatus.value == DownloadStatus.Idle) {
                    val readyFile =
                        withContext(Dispatchers.IO) { artifactDownloader.existingReadyFile(profile, artifact) }
                    if (readyFile != null) {
                        mutableDownloadStatus.value = DownloadStatus.ReadyToInstall(readyFile)
                    }
                }
            }
        }
    }

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

    // ignored while a download/install is actively in flight, same guard as startDownload/
    // startInstall - switching what's selected out from under an in-progress operation would let a
    // stale ReadyToInstall install the wrong (no longer selected) version, so this also resets
    // downloadStatus back to Idle for the newly selected artifact
    fun selectVersion(artifact: ArtifactInfo) {
        val status = mutableDownloadStatus.value
        if (isBusy() || status is DownloadStatus.Downloading || status is DownloadStatus.Verifying) return
        mutableExplicitArtifact.value = artifact
        mutableDownloadStatus.value = DownloadStatus.Idle
    }

    fun startDownload() {
        val currentApp = app.value
        val artifact = selectedArtifact.value
        if (currentApp == null || artifact == null || isBusy()) return
        if (mutableDownloadStatus.value is DownloadStatus.Downloading ||
            mutableDownloadStatus.value is DownloadStatus.Verifying
        ) {
            return
        }
        // a new download starts a new install attempt - a finished earlier one (an uninstall's
        // Success, say) would otherwise show as "Installed." once this download is ready
        mutableInstallStatus.value = InstallStatus.Idle
        // this keeps running for as long as the ViewModel itself is alive, which backgrounding the
        // app via Home does not affect - only leaving this screen (clearing the ViewModel) or the
        // process actually dying does. downloadProgressNotifier decides on its own whether a
        // notification is actually worth showing (it no-ops while the app is in the foreground,
        // where App Details' own progress bar already covers this) and whether a finished result
        // is worth surfacing even after the app comes back to the foreground
        viewModelScope.launch {
            artifactDownloader.download(currentApp, artifact).collect { status ->
                mutableDownloadStatus.value = status
                when (status) {
                    is DownloadStatus.Downloading ->
                        downloadProgressNotifier.onDownloading(
                            currentApp.id,
                            currentApp.displayName,
                            status.bytesDownloaded,
                            status.totalBytes,
                        )

                    is DownloadStatus.Verifying ->
                        downloadProgressNotifier.onVerifying(currentApp.id, currentApp.displayName)

                    is DownloadStatus.ReadyToInstall ->
                        downloadProgressNotifier.onComplete(currentApp.id, currentApp.displayName)

                    is DownloadStatus.Failed ->
                        downloadProgressNotifier.onFailed(currentApp.id, currentApp.displayName, status.reason)

                    is DownloadStatus.Idle -> downloadProgressNotifier.clear(currentApp.id)
                }
            }
        }
    }

    // leaving this screen mid-download cancels the download itself (viewModelScope goes with it) -
    // this makes sure a lingering progress notification doesn't outlive that
    override fun onCleared() {
        downloadProgressNotifier.clear(appId)
    }

    fun dismissAnnouncement(id: String) {
        viewModelScope.launch { announcementDismissalStore.dismiss(id) }
    }

    // a CLEAN_INSTALL app always goes through the orchestrator, section 16, 42.12 of the spec, and so
    // does installing an older build than the one on the device (a rollback) - Android refuses a
    // lower versionCode as an in-place update. Everything else attempts an in-place install first
    fun startInstall() {
        val currentApp = app.value
        val readyStatus = readyDownload()
        if (currentApp == null || readyStatus == null || isBusy()) return
        val apkFile = File(readyStatus.filePath)
        val flow =
            if (currentApp.installationMode == InstallationMode.CLEAN_INSTALL ||
                requiresUninstall(installedVersion.value, selectedArtifact.value)
            ) {
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

    // a standalone uninstall, independent of any download - reuses the same InstallationEngine the
    // clean-install path already drives for its own uninstall step, and the same system
    // confirmation-dialog flow (WaitingForUser(UNINSTALL_CONFIRM) -> Uninstalling -> Success/Failed)
    fun startUninstall() {
        val currentApp = app.value
        if (currentApp == null || installedVersion.value == null || isBusy()) return
        viewModelScope.launch {
            installationEngine.uninstall(currentApp.packageName).collect { status ->
                mutableInstallStatus.value = status
                if (status is InstallStatus.Success) {
                    recordActivity(currentApp, ActivityAction.UNINSTALLED)
                    managerBaselineStore.clear(currentApp.packageName)
                    // not shown as its own state: the page simply turns back into "Install"
                    mutableInstallStatus.value = InstallStatus.Idle
                    refresh()
                }
            }
        }
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
        // captured before the flow runs, not after: installedVersion reflects the OLD device state
        // right now, which is exactly what decides whether this is an install or an update
        val action = if (installedVersion.value != null) ActivityAction.UPDATED else ActivityAction.INSTALLED
        val installedArtifact = selectedArtifact.value
        viewModelScope.launch {
            flow.collect { status ->
                mutableInstallStatus.value = status
                if (status is InstallStatus.Success) {
                    // the downloaded apk is redundant once PackageInstaller has actually committed it -
                    // not deleted on failure, since a retry reuses this same file instead of re-downloading
                    readyDownload()?.let { artifactDownloader.deleteDownloadedFile(it.filePath) }
                    recordActivity(targetApp, action)
                    // this is now genuinely what the manager installed, real fact overriding whatever
                    // guess effectiveBaseline() would otherwise have made
                    if (installedArtifact != null) {
                        managerBaselineStore.recordInstall(targetApp.packageName, installedArtifact)
                    }
                    refresh()
                }
            }
        }
    }

    private suspend fun recordActivity(
        targetApp: AppProfile,
        action: ActivityAction,
    ) {
        activityLogRepository.record(
            ActivityEntry(
                id = UUID.randomUUID().toString(),
                appId = targetApp.id,
                appName = targetApp.displayName,
                action = action,
                timestampMillis = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
