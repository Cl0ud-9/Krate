package dev.cl0ud9.manager.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import dev.cl0ud9.manager.platform.packageinfo.InstalledPackageReader
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
) : ViewModel() {
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val mutableIsRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = mutableIsRefreshing.asStateFlow()

    val uiState: StateFlow<AppsUiState> =
        combine(catalogRepository.observeApps(), refreshTrigger.onStart { emit(Unit) }) { apps, _ -> apps }
            .map { apps -> toUiState(apps) }
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
            runCatching { catalogRepository.refresh() }
            mutableIsRefreshing.value = false
        }
    }

    private fun toUiState(apps: List<AppProfile>): AppsUiState {
        if (apps.isEmpty()) return AppsUiState.Empty
        val installed =
            apps
                .filter { installedPackageReader.installedVersion(it.packageName) != null }
                .mapTo(mutableSetOf()) { it.packageName }
        return AppsUiState.Content(apps, installed)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
