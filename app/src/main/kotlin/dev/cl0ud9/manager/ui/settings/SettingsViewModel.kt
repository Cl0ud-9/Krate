package dev.cl0ud9.manager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val automaticDownloads: StateFlow<Boolean> =
        settingsRepository
            .observeAutomaticDownloads()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    fun setAutomaticDownloads(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutomaticDownloads(enabled) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
