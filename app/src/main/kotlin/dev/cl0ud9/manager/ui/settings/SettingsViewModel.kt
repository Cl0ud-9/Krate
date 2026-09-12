package dev.cl0ud9.manager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.data.downloads.ArtifactDownloader
import dev.cl0ud9.manager.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val artifactDownloader: ArtifactDownloader,
) : ViewModel() {
    val automaticDownloads: StateFlow<Boolean> =
        settingsRepository
            .observeAutomaticDownloads()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    private val mutableCacheClearedMessage = MutableStateFlow<String?>(null)
    val cacheClearedMessage: StateFlow<String?> = mutableCacheClearedMessage.asStateFlow()

    fun setAutomaticDownloads(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutomaticDownloads(enabled) }
    }

    // downloaded apks are normally cleaned up right after a successful install (AppDetailsViewModel,
    // UpdateAllEngine) - this is the manual escape hatch for anything that missed that: an abandoned
    // download, or a leftover from a build before that cleanup existed
    fun clearCache() {
        viewModelScope.launch {
            val bytesFreed = withContext(Dispatchers.IO) { artifactDownloader.clearCache() }
            mutableCacheClearedMessage.value =
                if (bytesFreed > 0) "Freed ${formatMb(bytesFreed)}." else "Cache is already empty."
        }
    }

    private fun formatMb(bytes: Long): String = "%.1f MB".format(bytes / BYTES_PER_MB)

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
        const val BYTES_PER_MB = 1024f * 1024f
    }
}
