package dev.cl0ud9.krate.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.krate.data.downloads.ArtifactDownloader
import dev.cl0ud9.krate.domain.dependency.DependencyGraph
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
import dev.cl0ud9.krate.domain.repository.Baseline
import dev.cl0ud9.krate.domain.repository.CatalogRepository
import dev.cl0ud9.krate.domain.repository.KrateBaselineStore
import dev.cl0ud9.krate.domain.repository.effectiveBaseline
import dev.cl0ud9.krate.domain.repository.forTrack
import dev.cl0ud9.krate.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.krate.platform.packageinfo.InstalledVersion
import dev.cl0ud9.krate.platform.work.AppWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

data class DependencyInfo(
    val app: AppProfile,
    val installed: Boolean,
)

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
    private val installedPackageReader: InstalledPackageReader,
    private val activityLogRepository: ActivityLogRepository,
    private val krateBaselineStore: KrateBaselineStore,
    private val announcementDismissalStore: AnnouncementDismissalStore,
    // the app's download, install or uninstall, kept going when this page closes
    private val work: AppWork,
    private val appId: String,
) : ViewModel() {
    val app: StateFlow<AppProfile?> =
        catalogRepository
            .observeApp(appId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // the list has loaded and this app isn't in it: one stopped being tracked, or a link to an app the Krate dropped
    val missing: StateFlow<Boolean> =
        catalogRepository
            .observeApps()
            .map { apps -> apps.none { it.id == appId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    // the build Krate itself last installed for this app, or null if it never has - App
    // Details reads this alongside installedVersion (the live device state) to tell "up to date"
    // from "diverged outside Krate", see AppDetailsUiState.effectiveBaseline
    val krateBaseline: StateFlow<Baseline?> =
        combine(app, krateBaselineStore.observeBaselines()) { profile, baselines ->
            profile?.let { baselines[it.packageName] }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // installed state is device-local, so a resume-triggered refresh() re-checks it - a successful
    // install also refreshes immediately below, section 13 + 42.19 of the spec
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

    // what AppWork has under way for this app; it carries on whether or not this page is open
    val downloadStatus: StateFlow<DownloadStatus> =
        work
            .state(
                appId,
            ).map { it.download }
            .stateIn(viewModelScope, SharingStarted.Eagerly, work.current(appId).download)

    val installStatus: StateFlow<InstallStatus> =
        work
            .state(
                appId,
            ).map { it.install }
            .stateIn(viewModelScope, SharingStarted.Eagerly, work.current(appId).install)

    // null means "no explicit pick yet, use the newest" - only ever non-null once the user taps a
    // specific version in App Details' version history, section 9 of the spec (artifacts retains
    // more than just the latest so a broken newest build still leaves older ones installable)
    // a build already under way when the page opens is the one it shows
    private val mutableExplicitArtifact =
        MutableStateFlow(work.current(appId).takeIf { it.download !is DownloadStatus.Idle }?.artifact)
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
        // an install that went through while the page was closed: it opens settled, not on the success step
        if (work.current(appId).install is InstallStatus.Success) {
            work.setInstall(appId, InstallStatus.Idle)
            work.setDownload(appId, DownloadStatus.Idle)
        }
        settleWhenFinished()
        viewModelScope.launch {
            combine(app.filterNotNull(), selectedArtifact.filterNotNull()) { profile, artifact ->
                profile to artifact
            }.collect { (profile, artifact) ->
                if (work.current(appId).download == DownloadStatus.Idle) {
                    val readyFile =
                        withContext(Dispatchers.IO) { artifactDownloader.existingReadyFile(profile, artifact) }
                    if (readyFile != null) {
                        work.setDownload(appId, DownloadStatus.ReadyToInstall(readyFile))
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
                downloadStatus,
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
        if (!File(ready.filePath).exists()) work.setDownload(appId, DownloadStatus.Idle)
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
        work.setDownload(appId, DownloadStatus.Idle)
    }

    // drops a version picked in version history, back to the newest build
    fun backToLatest() {
        if (mutableExplicitArtifact.value == null || !canChangeVersion()) return
        mutableExplicitArtifact.value = null
        work.setDownload(appId, DownloadStatus.Idle)
    }

    private fun canChangeVersion(): Boolean = !isBusy() && !work.downloading(appId)

    fun startDownload() {
        val currentApp = app.value
        val artifact = selectedArtifact.value
        val alreadyGoing = isBusy() || work.downloading(appId)
        if (currentApp == null || artifact == null || alreadyGoing) return
        // a new download starts a new attempt: an earlier "installed" message doesn't belong to it
        mutableJustInstalled.value = false
        mutableRestorePending.value = false
        // Install, Update or Reinstall means the whole thing: the install follows the download by itself
        work.download(
            currentApp,
            artifact,
            installAfter = canInstallInPlace(currentApp, installedVersion.value, artifact),
        )
    }

    // stops an in-flight download; the partial file is kept, so starting again resumes it
    fun cancelDownload() = work.cancelDownload(appId)

    fun dismissAnnouncement(id: String) {
        viewModelScope.launch { announcementDismissalStore.dismiss(id) }
    }

    // a CLEAN_INSTALL app always goes through the orchestrator, section 16, 42.12 of the spec, and so does installing
    // an older build than the one on the device (a rollback): Android refuses a lower versionCode in place
    fun startInstall() {
        val currentApp = app.value
        val readyStatus = readyDownload()
        if (currentApp == null || readyStatus == null || isBusy()) return
        val apkFile = File(readyStatus.filePath)
        // the file went away since it was ready: fetch it again rather than fail on a missing file
        if (!apkFile.exists()) {
            work.setDownload(appId, DownloadStatus.Idle)
            startDownload()
            return
        }
        val fromScratch =
            currentApp.installationMode == InstallationMode.CLEAN_INSTALL ||
                requiresUninstall(installedVersion.value, selectedArtifact.value)
        work.install(currentApp, apkFile, selectedArtifact.value, fromScratch)
    }

    // explicit, user-confirmed fallback after a normal update failed, section 17 of the spec
    fun retryAsCleanInstall() {
        val currentApp = app.value
        val readyStatus = readyDownload()
        if (currentApp == null || readyStatus == null || isBusy()) return
        work.install(currentApp, File(readyStatus.filePath), selectedArtifact.value, fromScratch = true)
    }

    // through the system's confirmation, like the clean install's own uninstall step
    fun startUninstall() {
        val currentApp = app.value
        if (currentApp == null || installedVersion.value == null || isBusy()) return
        mutableJustInstalled.value = false
        mutableRestorePending.value = false
        work.uninstall(currentApp)
    }

    private fun resolveDependencies(
        profile: AppProfile,
        catalog: List<AppProfile>,
    ): List<DependencyInfo> =
        DependencyGraph.directDependencies(profile, catalog).map { dependency ->
            DependencyInfo(dependency, installedPackageReader.installedVersion(dependency.packageName) != null)
        }

    private fun readyDownload(): DownloadStatus.ReadyToInstall? =
        work.current(appId).download as? DownloadStatus.ReadyToInstall

    private fun isBusy(): Boolean = work.busy(appId)

    // each install or uninstall AppWork finishes: the page refreshes, and after an install says so where "Up to date."
    // would be, once Android reports the new version, so "Install" never flashes up meanwhile
    private fun settleWhenFinished() {
        viewModelScope.launch {
            var seen = work.current(appId).finished?.serial
            work
                .state(appId)
                .map { it.finished }
                .distinctUntilChanged()
                .collect { done ->
                    if (done == null || done.serial == seen) return@collect
                    seen = done.serial
                    if (done.uninstalled) refresh() else settleAfterInstall(done.erasedData)
                }
        }
    }

    private suspend fun settleAfterInstall(erasedData: Boolean) {
        val before = installedVersion.value
        refresh()
        withTimeoutOrNull(SETTLE_TIMEOUT_MS) { installedVersion.first { it != null && it != before } }
        mutableJustInstalled.value = true
        // only an app that was there (and so had settings) lost anything
        mutableRestorePending.value = erasedData
        work.setDownload(appId, DownloadStatus.Idle)
        work.setInstall(appId, InstallStatus.Idle)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
