package dev.cl0ud9.manager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.data.auth.GitHubCredentialStore
import dev.cl0ud9.manager.domain.model.ActivityEntry
import dev.cl0ud9.manager.domain.model.AnnouncementItem
import dev.cl0ud9.manager.domain.model.isActive
import dev.cl0ud9.manager.domain.model.isVisible
import dev.cl0ud9.manager.domain.repository.ActivityLogRepository
import dev.cl0ud9.manager.domain.repository.AnnouncementDismissalStore
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import dev.cl0ud9.manager.domain.repository.ManagerBaselineStore
import dev.cl0ud9.manager.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.manager.platform.packageinfo.isUpdateAvailable
import dev.cl0ud9.manager.platform.selfupdate.ManagerSelfUpdateInstaller
import dev.cl0ud9.manager.platform.selfupdate.ManagerUpdateChecker
import dev.cl0ud9.manager.platform.selfupdate.ManagerUpdateStatus
import dev.cl0ud9.manager.platform.selfupdate.SelfUpdateState
import dev.cl0ud9.manager.ui.util.withMinimumDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// each collaborator is a distinct shared singleton from AppContainer, not worth bundling just for the count
@Suppress("LongParameterList")
class HomeViewModel(
    private val catalogRepository: CatalogRepository,
    private val installedPackageReader: InstalledPackageReader,
    activityLogRepository: ActivityLogRepository,
    private val managerUpdateChecker: ManagerUpdateChecker,
    private val githubCredentialStore: GitHubCredentialStore,
    private val managerBaselineStore: ManagerBaselineStore,
    private val announcementDismissalStore: AnnouncementDismissalStore,
    private val managerSelfUpdateInstaller: ManagerSelfUpdateInstaller,
) : ViewModel() {
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val mutableIsRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = mutableIsRefreshing.asStateFlow()

    private val mutableRefreshFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshFailed: SharedFlow<Unit> = mutableRefreshFailed.asSharedFlow()

    private val mutableUpdateAnnouncement = MutableStateFlow<ManagerUpdateStatus.UpdateAvailable?>(null)
    val updateAnnouncement: StateFlow<ManagerUpdateStatus.UpdateAvailable?> = mutableUpdateAnnouncement.asStateFlow()

    // filtered the same way Apps/Updates are - a requiresAuth app without a token, or one the
    // catalog itself disabled, should not count towards these totals either
    private val refreshedApps =
        combine(catalogRepository.observeApps(), refreshTrigger.onStart { emit(Unit) }) { apps, _ -> apps }
            .map { apps -> apps.filter { it.isVisible(githubCredentialStore.getToken() != null) } }

    val catalogCount: StateFlow<Int> =
        refreshedApps
            .map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    // real device-installed vs catalog-latest comparison, section 13 + 42.19 of the spec.
    // installedVersion() is a real PackageManager Binder call per app - flowOn(IO) keeps this
    // (and installedCount below) off the main thread, same reasoning as Apps/Updates' identical fix
    val pendingUpdateCount: StateFlow<Int> =
        combine(refreshedApps, managerBaselineStore.observeBaselines()) { apps, baselines ->
            apps.count { app ->
                val installed = installedPackageReader.installedVersion(app.packageName)
                isUpdateAvailable(installed, app, baselines[app.packageName])
            }
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    val installedCount: StateFlow<Int> =
        refreshedApps
            .map { apps -> apps.count { installedPackageReader.installedVersion(it.packageName) != null } }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    // general notices, plus ones about a specific app only while that app is installed here - a
    // notice like "this app was discontinued, here is an alternative" matters to people who have it
    val announcements: StateFlow<List<AnnouncementItem>> =
        combine(
            catalogRepository.observeAnnouncements(),
            announcementDismissalStore.observeDismissed(),
            catalogRepository.observeApps(),
            refreshTrigger.onStart { emit(Unit) },
        ) { announcements, dismissed, apps, _ ->
            val now = System.currentTimeMillis()
            val installedIds =
                apps.filter { installedPackageReader.installedVersion(it.packageName) != null }.map { it.id }.toSet()
            announcements
                .filter { it.isActive(now) && it.id !in dismissed }
                .filter { it.appIds.isEmpty() || it.appIds.any { id -> id in installedIds } }
                .map { announcement ->
                    AnnouncementItem(announcement, apps.find { it.id == announcement.actionAppId }?.displayName)
                }
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val recentActivity: StateFlow<List<ActivityEntry>> =
        activityLogRepository
            .observeRecent()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    init {
        // a proactive "here's what's new" check instead of one tucked away in Settings the user has
        // to remember to open - runs once per ViewModel lifetime (this app's tab ViewModels survive
        // tab switches via Navigation's saveState/restoreState), not on every Home recomposition, so
        // it never spams GitHub's API. Silently does nothing for UpToDate/NoReleasePublished/Failed -
        // this is only for the genuinely actionable case
        viewModelScope.launch {
            val status = runCatching { managerUpdateChecker.check() }.getOrNull()
            if (status is ManagerUpdateStatus.UpdateAvailable) {
                mutableUpdateAnnouncement.value = status
            }
        }
    }

    fun dismissAnnouncement(id: String) {
        viewModelScope.launch { announcementDismissalStore.dismiss(id) }
    }

    private val mutableSelfUpdateState = MutableStateFlow<SelfUpdateState?>(null)
    val selfUpdateState: StateFlow<SelfUpdateState?> = mutableSelfUpdateState.asStateFlow()

    // the same in-app download + system install prompt Settings offers, straight from the dialog
    fun installManagerUpdate(downloadUrl: String) {
        val current = mutableSelfUpdateState.value
        if (current is SelfUpdateState.Downloading || current is SelfUpdateState.Installing) return
        viewModelScope.launch {
            managerSelfUpdateInstaller.downloadAndInstall(downloadUrl).collect { mutableSelfUpdateState.value = it }
        }
    }

    fun dismissUpdateAnnouncement() {
        mutableUpdateAnnouncement.value = null
    }

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

    // pull-to-refresh: the one path that actually re-fetches the manifest over the network (see
    // RemoteCatalogRepository) - guarded so a second pull while one is already in flight is a no-op
    // rather than a duplicate request
    fun refreshFromNetwork() {
        if (mutableIsRefreshing.value) return
        viewModelScope.launch {
            mutableIsRefreshing.value = true
            val result = withMinimumDuration { runCatching { catalogRepository.refresh() } }
            mutableIsRefreshing.value = false
            if (result.isFailure) mutableRefreshFailed.emit(Unit)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
