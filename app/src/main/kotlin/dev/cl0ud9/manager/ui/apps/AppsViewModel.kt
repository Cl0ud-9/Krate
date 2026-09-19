package dev.cl0ud9.manager.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.data.auth.GitHubCredentialStore
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.isVisible
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import dev.cl0ud9.manager.platform.packageinfo.InstalledPackageReader
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

sealed interface AppsUiState {
    data object Loading : AppsUiState

    data object Empty : AppsUiState

    data class Content(
        val apps: List<AppProfile>,
        val installedPackageNames: Set<String>,
    ) : AppsUiState
}

// installed state is device-local and can change outside the catalog flow (an install/uninstall does not
// itself emit a new manifest), so a resume-triggered refresh() re-checks it - section 13 + 42.19 of the spec
class AppsViewModel(
    private val catalogRepository: CatalogRepository,
    private val installedPackageReader: InstalledPackageReader,
    private val githubCredentialStore: GitHubCredentialStore,
) : ViewModel() {
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val mutableIsRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = mutableIsRefreshing.asStateFlow()

    // one-shot: a failed pull-to-refresh used to just stop the spinner with zero feedback - the
    // catalog itself never goes empty on a failure (RemoteCatalogRepository always falls back to
    // the last cache or the seed asset, section 32), so this exists purely to tell the user their
    // explicit refresh attempt specifically didn't reach the network, not to signal missing data
    private val mutableRefreshFailed = MutableSharedFlow<Unit>()
    val refreshFailed: SharedFlow<Unit> = mutableRefreshFailed.asSharedFlow()

    // toUiState() calls installedPackageReader.installedVersion() (a real PackageManager Binder
    // call) once per catalog app - flowOn(IO) keeps that whole loop off the main thread, which
    // otherwise blocked right during this screen's own enter transition on every single visit
    // (RefreshOnResume re-triggers this on every return to the tab, not just first load)
    val uiState: StateFlow<AppsUiState> =
        combine(catalogRepository.observeApps(), refreshTrigger.onStart { emit(Unit) }) { apps, _ -> apps }
            .map { apps -> toUiState(apps) }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppsUiState.Loading)

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

    private fun toUiState(apps: List<AppProfile>): AppsUiState {
        val hasToken = githubCredentialStore.getToken() != null
        // alphabetical (case-insensitive), not catalog/manifest order - the manifest's own app
        // array order is just whatever catalog-metadata.json happens to list them in, not a
        // deliberate display order
        val visibleApps = apps.filter { it.isVisible(hasToken) }.sortedBy { it.displayName.lowercase() }
        if (visibleApps.isEmpty()) return AppsUiState.Empty
        val installed =
            visibleApps
                .filter { installedPackageReader.installedVersion(it.packageName) != null }
                .mapTo(mutableSetOf()) { it.packageName }
        return AppsUiState.Content(visibleApps, installed)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
