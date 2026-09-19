package dev.cl0ud9.manager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.data.auth.GitHubCredentialStore
import dev.cl0ud9.manager.data.downloads.ArtifactDownloader
import dev.cl0ud9.manager.data.settings.DEFAULT_NAV_BAR_CORNER_RADIUS
import dev.cl0ud9.manager.domain.model.LaunchTab
import dev.cl0ud9.manager.domain.model.NavBarStyle
import dev.cl0ud9.manager.domain.model.ThemeMode
import dev.cl0ud9.manager.domain.repository.ActivityLogRepository
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import dev.cl0ud9.manager.domain.repository.SettingsRepository
import dev.cl0ud9.manager.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.manager.platform.selfupdate.ManagerSelfUpdateInstaller
import dev.cl0ud9.manager.platform.selfupdate.ManagerUpdateChecker
import dev.cl0ud9.manager.platform.selfupdate.ManagerUpdateStatus
import dev.cl0ud9.manager.platform.selfupdate.SelfUpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

@Suppress("TooManyFunctions", "LongParameterList")
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val artifactDownloader: ArtifactDownloader,
    private val managerUpdateChecker: ManagerUpdateChecker,
    private val managerSelfUpdateInstaller: ManagerSelfUpdateInstaller,
    private val githubCredentialStore: GitHubCredentialStore,
    private val catalogRepository: CatalogRepository,
    private val installedPackageReader: InstalledPackageReader,
    private val activityLogRepository: ActivityLogRepository,
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

    private val mutableSelfUpdateState = MutableStateFlow<SelfUpdateState?>(null)
    val selfUpdateState: StateFlow<SelfUpdateState?> = mutableSelfUpdateState.asStateFlow()

    // never surfaces the token value itself back to the UI, only whether one is currently saved -
    // EncryptedSharedPreferences has no Flow of its own, so this is refreshed manually on set/clear
    private val mutableHasGitHubToken = MutableStateFlow(githubCredentialStore.getToken() != null)
    val hasGitHubToken: StateFlow<Boolean> = mutableHasGitHubToken.asStateFlow()

    private val mutableFeedbackText = MutableStateFlow("")
    val feedbackText: StateFlow<String> = mutableFeedbackText.asStateFlow()

    private val mutableDiagnosticReport = MutableStateFlow<String?>(null)
    val diagnosticReport: StateFlow<String?> = mutableDiagnosticReport.asStateFlow()

    private val mutableGeneratingReport = MutableStateFlow(false)
    val generatingReport: StateFlow<Boolean> = mutableGeneratingReport.asStateFlow()

    fun setFeedbackText(text: String) {
        mutableFeedbackText.value = text
    }

    // deviceSummary is gathered by the caller (rememberDeviceSummary(), UI layer) since it's plain
    // Context/PackageManager facts, not app state this ViewModel otherwise owns - see that
    // composable's own comment for why. Everything below IS this ViewModel's own state, so it stays
    // here: catalog size, install count, and recent activity all come from the same repositories
    // Home/Apps/Updates already read, just assembled into one text blob instead of separate StateFlows
    fun generateDiagnosticReport(deviceSummary: String) {
        if (mutableGeneratingReport.value) return
        viewModelScope.launch {
            mutableGeneratingReport.value = true
            val report =
                withContext(Dispatchers.IO) {
                    val apps = catalogRepository.observeApps().first()
                    val installedCount = apps.count { installedPackageReader.installedVersion(it.packageName) != null }
                    val recentActivity = activityLogRepository.observeRecent().first()
                    formatDiagnosticReport(deviceSummary, apps.size, installedCount, recentActivity)
                }
            mutableDiagnosticReport.value = report
            mutableGeneratingReport.value = false
        }
    }

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

    // downloads the release apk and hands it to PackageInstaller, which raises Android's own
    // install-confirmation dialog - that system dialog IS the "prompt to update" this replaces
    // opening the GitHub release page with. Guarded the same way checkForManagerUpdate() is: a
    // second tap while one is already running is a no-op rather than starting a duplicate download
    fun installManagerUpdate(downloadUrl: String) {
        if (mutableSelfUpdateState.value is SelfUpdateState.Downloading) return
        viewModelScope.launch {
            managerSelfUpdateInstaller.downloadAndInstall(downloadUrl).collect { state ->
                mutableSelfUpdateState.value = state
            }
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

    // read only by artifacts whose manifest entry is requiresAuth (the Patched-style apps, hosted
    // as published releases on a shared private artifacts repo) - a read-only "Contents" token
    // scoped to that one repo is all it ever needs, see SETUP.md section 4
    fun setGitHubToken(token: String) {
        githubCredentialStore.setToken(token)
        mutableHasGitHubToken.value = true
    }

    fun clearGitHubToken() {
        githubCredentialStore.clearToken()
        mutableHasGitHubToken.value = false
    }

    private fun formatMb(bytes: Long): String = "%.1f MB".format(bytes / BYTES_PER_MB)

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
        const val BYTES_PER_MB = 1024f * 1024f
    }
}
