package dev.cl0ud9.krate.ui.details

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.krate.data.activity.DOWNLOAD_FAILURE_PREFIX
import dev.cl0ud9.krate.data.activity.INSTALL_FAILURE_PREFIX
import dev.cl0ud9.krate.data.downloads.ArtifactDownloader
import dev.cl0ud9.krate.data.downloads.DownloadProgressNotifier
import dev.cl0ud9.krate.domain.dependency.DependencyGraph
import dev.cl0ud9.krate.domain.installer.CleanInstallOrchestrator
import dev.cl0ud9.krate.domain.installer.InstallationEngine
import dev.cl0ud9.krate.domain.model.ActivityAction
import dev.cl0ud9.krate.domain.model.ActivityEntry
import dev.cl0ud9.krate.domain.model.AnnouncementItem
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.ArtifactInfo
import dev.cl0ud9.krate.domain.model.DownloadStatus
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.model.isActive
import dev.cl0ud9.krate.domain.model.latestArtifact
import dev.cl0ud9.krate.domain.repository.ActivityLogRepository
import dev.cl0ud9.krate.domain.repository.AnnouncementDismissalStore
import dev.cl0ud9.krate.domain.repository.AutoUpdateStore
import dev.cl0ud9.krate.domain.repository.Baseline
import dev.cl0ud9.krate.domain.repository.CatalogRepository
import dev.cl0ud9.krate.domain.repository.KrateBaselineStore
import dev.cl0ud9.krate.domain.repository.buildKey
import dev.cl0ud9.krate.domain.repository.effectiveBaseline
import dev.cl0ud9.krate.domain.repository.forTrack
import dev.cl0ud9.krate.domain.repository.newerThanPicked
import dev.cl0ud9.krate.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.krate.platform.packageinfo.InstalledVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID

data class DependencyInfo(
    val app: AppProfile,
    val installed: Boolean,
)

private const val MIN_ATTEMPT_VISIBLE_MS = 600L

// how long a finished install waits for Android to report the new version before the page settles anyway
private const val SETTLE_TIMEOUT_MS = 3000L

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
    private val krateBaselineStore: KrateBaselineStore,
    private val downloadProgressNotifier: DownloadProgressNotifier,
    private val announcementDismissalStore: AnnouncementDismissalStore,
    private val autoUpdateStore: AutoUpdateStore,
    private val appId: String,
) : ViewModel() {
    val app: StateFlow<AppProfile?> =
        catalogRepository
            .observeApp(appId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // the build Krate itself last installed for this app, or null if it never has - App
    // Details reads this alongside installedVersion (the live device state) to tell "up to date"
    // from "diverged outside Krate", see AppDetailsUiState.effectiveBaseline
    val krateBaseline: StateFlow<Baseline?> =
        combine(app, krateBaselineStore.observeBaselines()) { profile, baselines ->
            profile?.let { baselines[it.packageName] }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // installed state is device-local, so a resume-triggered refresh() re-checks it - a successful
    // install also refreshes immediately below, section 13 + 42.19 of the spec
    // set by a tap on Install, Update or Reinstall, cleared once acted on (see installIfStillWanted)
    private var installWhenReady = false

    // an install just finished on this screen: its message stands in for "Up to date." until the next action
    private val mutableJustInstalled = MutableStateFlow(false)
    val justInstalled: StateFlow<Boolean> = mutableJustInstalled.asStateFlow()

    // that install erased the app's data first (a rollback or reinstall from scratch): time to bring settings back
    private val mutableRestorePending = MutableStateFlow(false)
    val restorePending: StateFlow<Boolean> = mutableRestorePending.asStateFlow()

    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // installedVersion() is a real PackageManager Binder call, not free - flowOn(IO) keeps it off
    // the main thread, which otherwise blocked right during this screen's own enter transition
    // (collapsing header, depth-blur) every single time it opened
    val installedVersion: StateFlow<InstalledVersion?> =
        combine(app, refreshTrigger.onStart { emit(Unit) }) { profile, _ -> profile }
            .map { profile ->
                val installed = profile?.let { installedPackageReader.installedVersion(it.packageName) }
                // removed outside Krate: its record of the install goes too, so a later reinstall starts fresh
                if (profile != null && installed == null) krateBaselineStore.clear(profile.packageName)
                installed
            }.flowOn(Dispatchers.IO)
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

    private var downloadJob: Job? = null

    private val mutableDownloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = mutableDownloadStatus.asStateFlow()

    private val mutableInstallStatus = MutableStateFlow<InstallStatus>(InstallStatus.Idle)
    val installStatus: StateFlow<InstallStatus> = mutableInstallStatus.asStateFlow()

    // null means "no explicit pick yet, use the newest" - only ever non-null once the user taps a
    // specific version in App Details' version history, section 9 of the spec (artifacts retains
    // more than just the latest so a broken newest build still leaves older ones installable)
    private val mutableExplicitArtifact = MutableStateFlow<ArtifactInfo?>(null)
    val selectedArtifact: StateFlow<ArtifactInfo?> =
        combine(
            app,
            mutableExplicitArtifact,
            krateBaseline,
            installedVersion,
        ) { profile, explicit, recorded, installed ->
            // the newest build of the installed theme, for apps that publish several side by side
            explicit ?: profile?.let { it.forTrack(effectiveBaseline(recorded, it, installed)).latestArtifact }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // the screen opens once it knows whether a download is already waiting, so its card doesn't swap in late
    private val mutableFirstCheckDone = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = mutableFirstCheckDone.asStateFlow()

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
                        // fetched some other way (automatic downloads, an earlier run), so an old failure is moot
                        activityLogRepository.clearFailures(profile.id, downloadsOnly = true)
                    }
                }
                mutableFirstCheckDone.value = true
            }
        }
        viewModelScope.launch {
            combine(
                InstallRequests.requested,
                mutableFirstCheckDone,
                mutableDownloadStatus,
            ) { requested, done, status ->
                Triple(appId in requested, done, status)
            }.collect { (wanted, done, status) -> if (wanted && done) handleInstallRequest(status) }
        }
    }

    // the notification's Install: installs the ready download (or fetches it again if it's gone) when one tap may,
    // judged on the phone's real state, read fresh; a rollback, an erase-first install or a missing required app
    // (MicroG) just opens the page on its own deliberate button
    private suspend fun handleInstallRequest(status: DownloadStatus) {
        val waiting = status is DownloadStatus.Downloading || status is DownloadStatus.Verifying
        val currentApp = app.value?.takeUnless { waiting || isBusy() } ?: return
        val (installed, unmet) =
            withContext(Dispatchers.IO) {
                val catalog = catalogRepository.observeApps().first()
                installedPackageReader.installedVersion(currentApp.packageName) to
                    resolveDependencies(currentApp, catalog).filterNot { it.installed }
            }
        // routing reads installedVersion, so it has to have caught up with the phone first
        withTimeoutOrNull(SETTLE_TIMEOUT_MS) { installedVersion.first { it == installed } }
        val oneTap = unmet.isEmpty() && canInstallInPlace(currentApp, installed, selectedArtifact.value)
        when {
            !InstallRequests.consume(appId) || !oneTap -> Unit
            status is DownloadStatus.ReadyToInstall -> startInstall()
            status is DownloadStatus.Idle -> startDownload()
        }
    }

    fun refresh() {
        dropStaleReadyFile()
        refreshTrigger.tryEmit(Unit)
    }

    // a ready download whose file is gone (installed and cleaned up, or the cache cleared) isn't ready any more
    private fun dropStaleReadyFile() {
        val ready = readyDownload() ?: return
        if (!File(ready.filePath).exists()) mutableDownloadStatus.value = DownloadStatus.Idle
    }

    // ignored while a download/install is actively in flight, same guard as startDownload/
    // startInstall - switching what's selected out from under an in-progress operation would let a
    // stale ReadyToInstall install the wrong (no longer selected) version, so this also resets
    // downloadStatus back to Idle for the newly selected artifact
    fun selectVersion(artifact: ArtifactInfo) {
        if (!canChangeVersion()) return
        mutableJustInstalled.value = false
        mutableRestorePending.value = false
        mutableExplicitArtifact.value = artifact
        mutableDownloadStatus.value = DownloadStatus.Idle
    }

    // drops a version picked in version history, back to the newest build
    fun backToLatest() {
        if (mutableExplicitArtifact.value == null || !canChangeVersion()) return
        mutableExplicitArtifact.value = null
        mutableDownloadStatus.value = DownloadStatus.Idle
    }

    private fun canChangeVersion(): Boolean {
        val status = mutableDownloadStatus.value
        return !isBusy() && status !is DownloadStatus.Downloading && status !is DownloadStatus.Verifying
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
        mutableJustInstalled.value = false
        mutableRestorePending.value = false
        // the tap shows at once, even before the first byte arrives
        mutableDownloadStatus.value = DownloadStatus.Downloading(0L, null)
        // Install, Update or Reinstall means the whole thing: the install follows the download by itself
        installWhenReady = canInstallInPlace(currentApp, installedVersion.value, artifact)
        // told right away, while Krate is still on screen: that's when it can keep the network for the whole
        // download, which then runs for as long as this screen's ViewModel lives, app backgrounded or not
        downloadProgressNotifier.onDownloading(currentApp.id, currentApp.displayName, 0L, null)
        downloadJob =
            viewModelScope.launch {
                collectDownload(currentApp, artifact)
            }
    }

    // stops an in-flight download; the partial file is kept, so starting again resumes it
    fun cancelDownload() {
        installWhenReady = false
        downloadJob?.cancel()
        downloadJob = null
        mutableDownloadStatus.value = DownloadStatus.Idle
        downloadProgressNotifier.clear(appId)
    }

    private suspend fun collectDownload(
        currentApp: AppProfile,
        artifact: ArtifactInfo,
    ) {
        val startedAt = System.currentTimeMillis()
        artifactDownloader.download(currentApp, artifact).collect { status ->
            // an instant failure (offline, say) still shows the attempt, so Try again visibly does something
            if (status is DownloadStatus.Failed) {
                delay(
                    MIN_ATTEMPT_VISIBLE_MS - (System.currentTimeMillis() - startedAt),
                )
            }
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

                is DownloadStatus.ReadyToInstall -> {
                    downloadProgressNotifier.onComplete(currentApp)
                    activityLogRepository.clearFailures(currentApp.id, downloadsOnly = true)
                    installIfStillWanted()
                }

                is DownloadStatus.Failed -> {
                    installWhenReady = false
                    downloadProgressNotifier.onFailed(currentApp, status.reason)
                    recordActivity(currentApp, ActivityAction.FAILED, "$DOWNLOAD_FAILURE_PREFIX ${status.reason}")
                }

                is DownloadStatus.Idle -> downloadProgressNotifier.clear(currentApp.id)
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
    // only while Krate is on screen: a download that finished in the background waits behind its notification instead,
    // and never past a required app (MicroG) that isn't installed yet
    private fun installIfStillWanted() {
        val wanted = installWhenReady
        installWhenReady = false
        val onScreen =
            ProcessLifecycleOwner
                .get()
                .lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED)
        if (wanted && onScreen && dependencies.value.all { it.installed }) startInstall()
    }

    fun startInstall() {
        val currentApp = app.value
        val readyStatus = readyDownload()
        if (currentApp == null || readyStatus == null || isBusy()) return
        val apkFile = File(readyStatus.filePath)
        // the file went away since it was ready: fetch it again rather than fail on a missing file
        if (!apkFile.exists()) {
            mutableDownloadStatus.value = DownloadStatus.Idle
            startDownload()
            return
        }
        val fromScratch =
            currentApp.installationMode == InstallationMode.CLEAN_INSTALL ||
                requiresUninstall(installedVersion.value, selectedArtifact.value)
        val flow =
            if (fromScratch) {
                cleanInstallOrchestrator.cleanInstall(currentApp, apkFile)
            } else {
                installationEngine.install(currentApp, apkFile)
            }
        runInstallFlow(flow, currentApp, fromScratch)
    }

    // explicit, user-confirmed fallback after a normal update failed, section 17 of the spec
    fun retryAsCleanInstall() {
        val currentApp = app.value
        val readyStatus = readyDownload()
        if (currentApp == null || readyStatus == null || isBusy()) return
        runInstallFlow(cleanInstallOrchestrator.cleanInstall(currentApp, File(readyStatus.filePath)), currentApp, true)
    }

    // a standalone uninstall, independent of any download - reuses the same InstallationEngine the
    // clean-install path already drives for its own uninstall step, and the same system
    // confirmation-dialog flow (WaitingForUser(UNINSTALL_CONFIRM) -> Uninstalling -> Success/Failed)
    fun startUninstall() {
        val currentApp = app.value
        if (currentApp == null || installedVersion.value == null || isBusy()) return
        mutableJustInstalled.value = false
        mutableRestorePending.value = false
        viewModelScope.launch {
            installationEngine.uninstall(currentApp.packageName).collect { status ->
                mutableInstallStatus.value = status
                if (status is InstallStatus.Success) {
                    recordActivity(currentApp, ActivityAction.UNINSTALLED)
                    krateBaselineStore.clear(currentApp.packageName)
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
        fromScratch: Boolean = false,
    ) {
        // captured before the flow runs, not after: installedVersion reflects the OLD device state
        // right now, which is exactly what decides whether this is an install or an update
        val action = if (installedVersion.value != null) ActivityAction.UPDATED else ActivityAction.INSTALLED
        val installedArtifact = selectedArtifact.value
        viewModelScope.launch {
            flow.collect { status ->
                mutableInstallStatus.value = status
                if (status is InstallStatus.Failed && !status.userCancelled) {
                    recordActivity(targetApp, ActivityAction.FAILED, "$INSTALL_FAILURE_PREFIX ${status.reason}")
                }
                if (status is InstallStatus.Success) {
                    // the downloaded apk is redundant once PackageInstaller has actually committed it -
                    // not deleted on failure, since a retry reuses this same file instead of re-downloading
                    readyDownload()?.let { artifactDownloader.deleteDownloadedFile(it.filePath) }
                    activityLogRepository.clearFailures(targetApp.id)
                    recordActivity(targetApp, action)
                    // this is now genuinely what Krate installed, real fact overriding whatever
                    // guess effectiveBaseline() would otherwise have made
                    if (installedArtifact != null) {
                        krateBaselineStore.recordInstall(targetApp.packageName, installedArtifact)
                        // a rollback stays put: the build it stepped back from never comes back by itself
                        val steppedBackFrom = targetApp.newerThanPicked(installedArtifact)
                        if (steppedBackFrom != null) autoUpdateStore.skip(targetApp.id, steppedBackFrom.buildKey)
                    }
                    // only an app that was there (and so had settings) lost anything
                    mutableRestorePending.value = fromScratch && action == ActivityAction.UPDATED
                    settleAfterInstall()
                }
            }
        }
    }

    // the page goes straight to its normal state for the app as it is now (Open, Reinstall), the success message
    // standing in for "Up to date." - once Android reports the new install, so "Install" never flashes up meanwhile
    private suspend fun settleAfterInstall() {
        val before = installedVersion.value
        refresh()
        withTimeoutOrNull(SETTLE_TIMEOUT_MS) { installedVersion.first { it != null && it != before } }
        mutableJustInstalled.value = true
        mutableDownloadStatus.value = DownloadStatus.Idle
        mutableInstallStatus.value = InstallStatus.Idle
    }

    private suspend fun recordActivity(
        targetApp: AppProfile,
        action: ActivityAction,
        detail: String? = null,
    ) {
        activityLogRepository.record(
            ActivityEntry(
                id = UUID.randomUUID().toString(),
                appId = targetApp.id,
                appName = targetApp.displayName,
                action = action,
                timestampMillis = System.currentTimeMillis(),
                detail = detail,
            ),
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
