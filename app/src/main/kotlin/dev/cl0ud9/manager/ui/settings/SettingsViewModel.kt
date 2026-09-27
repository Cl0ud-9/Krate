package dev.cl0ud9.manager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.data.auth.GitHubCredentialStore
import dev.cl0ud9.manager.data.downloads.ArtifactDownloader
import dev.cl0ud9.manager.data.settings.DEFAULT_NAV_BAR_CORNER_RADIUS
import dev.cl0ud9.manager.domain.model.LaunchTab
import dev.cl0ud9.manager.domain.model.NavBarStyle
import dev.cl0ud9.manager.domain.model.ThemeMode
import dev.cl0ud9.manager.domain.model.latestArtifact
import dev.cl0ud9.manager.domain.repository.ActivityLogRepository
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import dev.cl0ud9.manager.domain.repository.ManagerBaselineStore
import dev.cl0ud9.manager.domain.repository.SettingsRepository
import dev.cl0ud9.manager.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.manager.platform.selfupdate.ManagerSelfUpdateInstaller
import dev.cl0ud9.manager.platform.selfupdate.ManagerUpdateChecker
import dev.cl0ud9.manager.platform.selfupdate.ManagerUpdateStatus
import dev.cl0ud9.manager.platform.selfupdate.SelfUpdateState
import dev.cl0ud9.manager.ui.util.withMinimumDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
    private val managerBaselineStore: ManagerBaselineStore,
) : ViewModel() {
    // real values from the first frame, so a revisited page doesn't animate from defaults to the saved state
    private val saved = settingsRepository.currentSettings()

    val automaticDownloads: StateFlow<Boolean> =
        settingsRepository.observeAutomaticDownloads().stateInPage(saved?.automaticDownloads ?: true)

    val downloadOnMobileData: StateFlow<Boolean> =
        settingsRepository.observeDownloadOnMobileData().stateInPage(saved?.downloadOnMobileData ?: false)

    val themeMode: StateFlow<ThemeMode> =
        settingsRepository.observeThemeMode().stateInPage(saved?.themeMode ?: ThemeMode.SYSTEM)

    val navBarStyle: StateFlow<NavBarStyle> =
        settingsRepository.observeNavBarStyle().stateInPage(saved?.navBarStyle ?: NavBarStyle.FLOATING_PILL)

    val navBarCornerRadius: StateFlow<Int> =
        settingsRepository.observeNavBarCornerRadius().stateInPage(
            saved?.navBarCornerRadius ?: DEFAULT_NAV_BAR_CORNER_RADIUS,
        )

    val navBarCompactMode: StateFlow<Boolean> =
        settingsRepository.observeNavBarCompactMode().stateInPage(saved?.navBarCompactMode ?: false)

    val useSmoothCorners: StateFlow<Boolean> =
        settingsRepository.observeUseSmoothCorners().stateInPage(saved?.useSmoothCorners ?: true)

    val disableBlur: StateFlow<Boolean> =
        settingsRepository.observeDisableBlur().stateInPage(saved?.disableBlur ?: false)

    val defaultLaunchTab: StateFlow<LaunchTab> =
        settingsRepository.observeDefaultLaunchTab().stateInPage(saved?.defaultLaunchTab ?: LaunchTab.HOME)

    private fun <T> Flow<T>.stateInPage(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    private val mutableCacheClearedMessage = MutableStateFlow<String?>(null)
    val cacheClearedMessage: StateFlow<String?> = mutableCacheClearedMessage.asStateFlow()

    private val mutableManagerUpdateState = MutableStateFlow<ManagerUpdateUiState>(ManagerUpdateUiState.Idle)
    val managerUpdateState: StateFlow<ManagerUpdateUiState> = mutableManagerUpdateState.asStateFlow()

    private val mutableSelfUpdateState = MutableStateFlow<SelfUpdateState?>(null)
    val selfUpdateState: StateFlow<SelfUpdateState?> = mutableSelfUpdateState.asStateFlow()

    // checked as soon as Settings opens (a single small GitHub API call), so an available update is
    // shown straight away instead of waiting for a tap on "Check for updates"
    init {
        checkForManagerUpdate(revalidate = false)
    }

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
                    val baselines = managerBaselineStore.observeBaselines().first()
                    val apps =
                        catalogRepository.observeApps().first().map { app ->
                            ReportedApp(
                                name = app.displayName,
                                installedVersion =
                                    installedPackageReader
                                        .installedVersion(
                                            app.packageName,
                                        )?.versionName,
                                latest = app.latestArtifact?.let { reportedVersion(it.versionName, it.buildId) },
                                installedByManager =
                                    baselines[app.packageName]?.let {
                                        reportedVersion(
                                            it.versionName,
                                            it.buildId,
                                        )
                                    },
                            )
                        }
                    val recentActivity = activityLogRepository.observeRecent().first()
                    formatDiagnosticReport(
                        deviceSummary = deviceSummary,
                        apps = apps,
                        recentActivity = recentActivity,
                        hasGitHubToken = githubCredentialStore.getToken() != null,
                        generatedAtMillis = System.currentTimeMillis(),
                    )
                }
            mutableDiagnosticReport.value = report
            mutableGeneratingReport.value = false
        }
    }

    fun setAutomaticDownloads(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutomaticDownloads(enabled) }
    }

    fun setDownloadOnMobileData(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDownloadOnMobileData(enabled) }
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
    // revalidate is on for a tap, off for the automatic check when Settings opens
    fun checkForManagerUpdate(revalidate: Boolean = true) {
        if (mutableManagerUpdateState.value is ManagerUpdateUiState.Checking) return
        viewModelScope.launch {
            mutableManagerUpdateState.value = ManagerUpdateUiState.Checking
            // a tapped check stays on screen long enough to read, however fast GitHub answers
            val status =
                if (revalidate) {
                    withMinimumDuration { managerUpdateChecker.check(revalidate = true) }
                } else {
                    managerUpdateChecker.check()
                }
            mutableManagerUpdateState.value = ManagerUpdateUiState.Result(status)
        }
    }

    // downloads the release apk and hands it to PackageInstaller, which raises Android's own
    // install-confirmation dialog - that system dialog IS the "prompt to update" this replaces
    // opening the GitHub release page with. Guarded the same way checkForManagerUpdate() is: a
    // second tap while one is already running is a no-op rather than starting a duplicate download
    fun installManagerUpdate(downloadUrl: String) {
        val current = mutableSelfUpdateState.value
        if (current is SelfUpdateState.Downloading || current is SelfUpdateState.Installing) return
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

    // read only by artifacts whose manifest entry is requiresAuth (the invite-only apps, hosted
    // as published releases on a shared private artifacts repo) - a read-only "Contents" token
    // scoped to that one repo is all it ever needs, see SETUP.md section 4
    // the invite-only entries come and go with the token, so the catalog is fetched again either way
    fun setGitHubToken(token: String) {
        githubCredentialStore.setToken(token)
        mutableHasGitHubToken.value = true
        viewModelScope.launch { runCatching { catalogRepository.refresh() } }
    }

    fun clearGitHubToken() {
        githubCredentialStore.clearToken()
        mutableHasGitHubToken.value = false
        viewModelScope.launch { runCatching { catalogRepository.refresh() } }
    }

    private fun formatMb(bytes: Long): String = "%.1f MB".format(bytes / BYTES_PER_MB)

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
        const val BYTES_PER_MB = 1024f * 1024f
    }
}
