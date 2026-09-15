package dev.cl0ud9.manager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.data.downloads.ArtifactDownloader
import dev.cl0ud9.manager.data.settings.DEFAULT_NAV_BAR_CORNER_RADIUS
import dev.cl0ud9.manager.domain.model.LaunchTab
import dev.cl0ud9.manager.domain.model.NavBarStyle
import dev.cl0ud9.manager.domain.model.ThemeMode
import dev.cl0ud9.manager.domain.repository.SettingsRepository
import dev.cl0ud9.manager.platform.selfupdate.ManagerUpdateChecker
import dev.cl0ud9.manager.platform.selfupdate.ManagerUpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ManagerUpdateUiState {
    data object Idle : ManagerUpdateUiState

    data object Checking : ManagerUpdateUiState

    data class Result(
        val status: ManagerUpdateStatus,
    ) : ManagerUpdateUiState
}

@Suppress("TooManyFunctions")
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val artifactDownloader: ArtifactDownloader,
    private val managerUpdateChecker: ManagerUpdateChecker,
) : ViewModel() {
    val automaticDownloads: StateFlow<Boolean> =
        settingsRepository
            .observeAutomaticDownloads()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    val themeMode: StateFlow<ThemeMode> =
        settingsRepository
            .observeThemeMode()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ThemeMode.SYSTEM)

    val navBarStyle: StateFlow<NavBarStyle> =
        settingsRepository
            .observeNavBarStyle()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NavBarStyle.FLOATING_PILL)

    val navBarCornerRadius: StateFlow<Int> =
        settingsRepository
            .observeNavBarCornerRadius()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DEFAULT_NAV_BAR_CORNER_RADIUS)

    val navBarCompactMode: StateFlow<Boolean> =
        settingsRepository
            .observeNavBarCompactMode()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val useSmoothCorners: StateFlow<Boolean> =
        settingsRepository
            .observeUseSmoothCorners()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

    val disableBlur: StateFlow<Boolean> =
        settingsRepository
            .observeDisableBlur()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val defaultLaunchTab: StateFlow<LaunchTab> =
        settingsRepository
            .observeDefaultLaunchTab()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LaunchTab.HOME)

    private val mutableCacheClearedMessage = MutableStateFlow<String?>(null)
    val cacheClearedMessage: StateFlow<String?> = mutableCacheClearedMessage.asStateFlow()

    private val mutableManagerUpdateState = MutableStateFlow<ManagerUpdateUiState>(ManagerUpdateUiState.Idle)
    val managerUpdateState: StateFlow<ManagerUpdateUiState> = mutableManagerUpdateState.asStateFlow()

    fun setAutomaticDownloads(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutomaticDownloads(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setNavBarStyle(style: NavBarStyle) {
        viewModelScope.launch { settingsRepository.setNavBarStyle(style) }
    }

    fun setNavBarCornerRadius(radius: Int) {
        viewModelScope.launch { settingsRepository.setNavBarCornerRadius(radius) }
    }

    fun setNavBarCompactMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNavBarCompactMode(enabled) }
    }

    fun setUseSmoothCorners(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUseSmoothCorners(enabled) }
    }

    fun setDisableBlur(disabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDisableBlur(disabled) }
    }

    fun setDefaultLaunchTab(tab: LaunchTab) {
        viewModelScope.launch { settingsRepository.setDefaultLaunchTab(tab) }
    }

    // amendment 44.2 of the spec: a manual, user-initiated check against the manager's own GitHub
    // Releases page - guarded so a second tap while one is already in flight is a no-op
    fun checkForManagerUpdate() {
        if (mutableManagerUpdateState.value is ManagerUpdateUiState.Checking) return
        viewModelScope.launch {
            mutableManagerUpdateState.value = ManagerUpdateUiState.Checking
            val status = managerUpdateChecker.check()
            mutableManagerUpdateState.value = ManagerUpdateUiState.Result(status)
        }
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
