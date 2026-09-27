package dev.cl0ud9.krate.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.krate.data.auth.GitHubCredentialStore
import dev.cl0ud9.krate.domain.model.ActivityEntry
import dev.cl0ud9.krate.domain.model.AnnouncementItem
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.isActive
import dev.cl0ud9.krate.domain.model.isVisible
import dev.cl0ud9.krate.domain.repository.ActivityLogRepository
import dev.cl0ud9.krate.domain.repository.AnnouncementDismissalStore
import dev.cl0ud9.krate.domain.repository.CatalogRepository
import dev.cl0ud9.krate.domain.repository.KrateBaselineStore
import dev.cl0ud9.krate.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.krate.platform.packageinfo.isUpdateAvailable
import dev.cl0ud9.krate.platform.selfupdate.KrateRelease
import dev.cl0ud9.krate.platform.selfupdate.KrateSelfUpdateInstaller
import dev.cl0ud9.krate.platform.selfupdate.KrateUpdateChecker
import dev.cl0ud9.krate.platform.selfupdate.KrateUpdateStatus
import dev.cl0ud9.krate.platform.selfupdate.SelfUpdateState
import dev.cl0ud9.krate.platform.selfupdate.WhatsNewTracker
import dev.cl0ud9.krate.ui.util.withMinimumDuration
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

// a catalog app as Home sees it
data class HomeApp(
    val app: AppProfile,
    val installed: Boolean,
    val hasUpdate: Boolean,
)

// each collaborator is a distinct shared singleton from AppContainer, not worth bundling just for the count
@Suppress("LongParameterList")
class HomeViewModel(
    private val catalogRepository: CatalogRepository,
    private val installedPackageReader: InstalledPackageReader,
    activityLogRepository: ActivityLogRepository,
    private val krateUpdateChecker: KrateUpdateChecker,
    private val githubCredentialStore: GitHubCredentialStore,
    private val krateBaselineStore: KrateBaselineStore,
    private val announcementDismissalStore: AnnouncementDismissalStore,
    private val krateSelfUpdateInstaller: KrateSelfUpdateInstaller,
    private val whatsNewTracker: WhatsNewTracker,
) : ViewModel() {
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val mutableIsRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = mutableIsRefreshing.asStateFlow()

    private val mutableRefreshFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshFailed: SharedFlow<Unit> = mutableRefreshFailed.asSharedFlow()

    private val mutableUpdateAnnouncement = MutableStateFlow<KrateUpdateStatus.UpdateAvailable?>(null)
    val updateAnnouncement: StateFlow<KrateUpdateStatus.UpdateAvailable?> = mutableUpdateAnnouncement.asStateFlow()

    // filtered the same way Apps/Updates are - a requiresAuth app without a token, or one the
    // catalog itself disabled, should not count towards these totals either
    private val refreshedApps =
        combine(catalogRepository.observeApps(), refreshTrigger.onStart { emit(Unit) }) { apps, _ -> apps }
            .map { apps -> apps.filter { it.isVisible(githubCredentialStore.getToken() != null) } }

    val catalogCount: StateFlow<Int> =
        refreshedApps
            .map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    // every visible catalog app with whether it's on this device and whether an update is waiting - one pass
    // of PackageManager lookups on IO feeds the counts, the Your apps row and the activity icons alike
    val homeApps: StateFlow<List<HomeApp>> =
        combine(refreshedApps, krateBaselineStore.observeBaselines()) { apps, baselines ->
            apps.map { app ->
                val installed = installedPackageReader.installedVersion(app.packageName)
                HomeApp(
                    app = app,
                    installed = installed != null,
                    hasUpdate = isUpdateAvailable(installed, app, baselines[app.packageName]),
                )
            }
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val pendingUpdateCount: StateFlow<Int> =
        homeApps
            .map { apps -> apps.count { it.hasUpdate } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    val installedCount: StateFlow<Int> =
        homeApps
            .map { apps -> apps.count { it.installed } }
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
        showWhatsNewAfterUpdate()
        viewModelScope.launch {
            val status = runCatching { krateUpdateChecker.check() }.getOrNull()
            if (status is KrateUpdateStatus.UpdateAvailable) {
                mutableUpdateAnnouncement.value = status
            }
        }
    }

    private val mutableWhatsNew = MutableStateFlow<KrateRelease?>(null)
    val whatsNew: StateFlow<KrateRelease?> = mutableWhatsNew.asStateFlow()

    // the first open after Krate updated itself shows that version's notes, once
    private fun showWhatsNewAfterUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            val version = whatsNewTracker.takeJustUpdatedVersion() ?: return@launch
            val release =
                runCatching { krateUpdateChecker.recentReleases() }.getOrNull()?.find {
                    it.version ==
                        version
                }
            if (release != null && release.notes.isNotBlank()) mutableWhatsNew.value = release
        }
    }

    fun dismissWhatsNew() {
        mutableWhatsNew.value = null
    }

    fun dismissAnnouncement(id: String) {
        viewModelScope.launch { announcementDismissalStore.dismiss(id) }
    }

    private val mutableSelfUpdateState = MutableStateFlow<SelfUpdateState?>(null)
    val selfUpdateState: StateFlow<SelfUpdateState?> = mutableSelfUpdateState.asStateFlow()

    // the same in-app download + system install prompt Settings offers, straight from the dialog
    fun installKrateUpdate(downloadUrl: String) {
        val current = mutableSelfUpdateState.value
        if (current is SelfUpdateState.Downloading || current is SelfUpdateState.Installing) return
        viewModelScope.launch {
            krateSelfUpdateInstaller.downloadAndInstall(downloadUrl).collect { mutableSelfUpdateState.value = it }
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
