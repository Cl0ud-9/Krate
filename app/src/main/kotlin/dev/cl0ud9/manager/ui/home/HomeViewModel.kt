package dev.cl0ud9.manager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    catalogRepository: CatalogRepository,
) : ViewModel() {
    val catalogCount: StateFlow<Int> =
        catalogRepository
            .observeApps()
            .map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    // update detection lands in phase 2, always zero until then
    val pendingUpdateCount: StateFlow<Int> = MutableStateFlow(0).asStateFlow()

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
