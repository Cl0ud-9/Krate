package dev.cl0ud9.manager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.data.auth.GitHubCredentialStore
import dev.cl0ud9.manager.domain.model.ActivityEntry
import dev.cl0ud9.manager.domain.model.isVisible
import dev.cl0ud9.manager.domain.model.latestVersionName
import dev.cl0ud9.manager.domain.repository.ActivityLogRepository
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import dev.cl0ud9.manager.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.manager.platform.packageinfo.isUpdateAvailable
import dev.cl0ud9.manager.platform.selfupdate.ManagerUpdateChecker
import dev.cl0ud9.manager.platform.selfupdate.ManagerUpdateStatus
import dev.cl0ud9.manager.ui.util.withMinimumDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val catalogRepository: CatalogRepository,
    private val installedPackageReader: InstalledPackageReader,
    activityLogRepository: ActivityLogRepository,
    private val managerUpdateChecker: ManagerUpdateChecker,
    private val githubCredentialStore: GitHubCredentialStore,
) : ViewModel() {
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val mutableIsRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = mutableIsRefreshing.asStateFlow()

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
        refreshedApps
            .map { apps ->
                apps.count { app ->
                    isUpdateAvailable(installedPackageReader.installedVersion(app.packageName), app.latestVersionName)
                }
            }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    val installedCount: StateFlow<Int> =
        refreshedApps
            .map { apps -> apps.count { installedPackageReader.installedVersion(it.packageName) != null } }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

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
            withMinimumDuration { runCatching { catalogRepository.refresh() } }
            mutableIsRefreshing.value = false
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
