package dev.cl0ud9.manager.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface AppsUiState {
    data object Loading : AppsUiState

    data object Empty : AppsUiState

    data class Content(
        val apps: List<AppProfile>,
    ) : AppsUiState
}

class AppsViewModel(
    catalogRepository: CatalogRepository,
) : ViewModel() {
    val uiState: StateFlow<AppsUiState> =
        catalogRepository
            .observeApps()
            .map { apps -> if (apps.isEmpty()) AppsUiState.Empty else AppsUiState.Content(apps) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppsUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
