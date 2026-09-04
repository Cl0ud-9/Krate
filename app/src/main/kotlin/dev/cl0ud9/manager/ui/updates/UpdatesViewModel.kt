package dev.cl0ud9.manager.ui.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import dev.cl0ud9.manager.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.manager.platform.packageinfo.isUpdateAvailable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

sealed interface UpdatesUiState {
    data object Loading : UpdatesUiState

    data object UpToDate : UpdatesUiState

    data class Content(
        val apps: List<AppProfile>,
    ) : UpdatesUiState
}

// apps whose installed version genuinely differs from the catalog's latest, section 13 + 42.19 of the spec.
// installed state is device-local, so a resume-triggered refresh() re-checks it after an install/uninstall
class UpdatesViewModel(
    catalogRepository: CatalogRepository,
    private val installedPackageReader: InstalledPackageReader,
) : ViewModel() {
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val uiState: StateFlow<UpdatesUiState> =
        combine(catalogRepository.observeApps(), refreshTrigger.onStart { emit(Unit) }) { apps, _ -> apps }
            .map { apps -> toUiState(apps) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UpdatesUiState.Loading)

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

    private fun toUiState(apps: List<AppProfile>): UpdatesUiState {
        val pending =
            apps.filter { app ->
                isUpdateAvailable(installedPackageReader.installedVersion(app.packageName), app.latestVersionName)
            }
        return if (pending.isEmpty()) UpdatesUiState.UpToDate else UpdatesUiState.Content(pending)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
