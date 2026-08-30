package dev.cl0ud9.manager.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AppDetailsViewModel(
    catalogRepository: CatalogRepository,
    appId: String,
) : ViewModel() {
    val app: StateFlow<AppProfile?> =
        catalogRepository
            .observeApp(appId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
