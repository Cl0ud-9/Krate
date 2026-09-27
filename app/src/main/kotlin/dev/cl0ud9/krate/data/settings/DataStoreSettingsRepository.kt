package dev.cl0ud9.krate.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.cl0ud9.krate.domain.model.LaunchTab
import dev.cl0ud9.krate.domain.model.NavBarStyle
import dev.cl0ud9.krate.domain.model.SettingsSnapshot
import dev.cl0ud9.krate.domain.model.ThemeMode
import dev.cl0ud9.krate.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

// the floating nav bar's corner radius range, in dp
const val MIN_NAV_BAR_CORNER_RADIUS = 0
const val MAX_NAV_BAR_CORNER_RADIUS = 60
const val DEFAULT_NAV_BAR_CORNER_RADIUS = 28

@Suppress("TooManyFunctions")
class DataStoreSettingsRepository(
    private val context: Context,
) : SettingsRepository {
    // kept hot for the app's lifetime; DataStore already holds the file in memory, this just keeps it mapped
    private val latest: StateFlow<SettingsSnapshot?> =
        context.settingsDataStore.data
            .map<Preferences, SettingsSnapshot?> { prefs -> prefs.toSnapshot() }
            .catch { emit(null) }
            .stateIn(CoroutineScope(SupervisorJob() + Dispatchers.IO), SharingStarted.Eagerly, null)

    override fun currentSettings(): SettingsSnapshot? = latest.value

    override fun observeAutomaticDownloads(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs.automaticDownloads() }

    override suspend fun setAutomaticDownloads(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[AUTOMATIC_DOWNLOADS] = enabled }
    }

    override fun observeDownloadOnMobileData(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs.downloadOnMobileData() }

    override suspend fun setDownloadOnMobileData(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[DOWNLOAD_ON_MOBILE_DATA] = enabled }
    }

    override fun observeOnboardingCompleted(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[ONBOARDING_COMPLETED] ?: false }

    override suspend fun setOnboardingCompleted() {
        context.settingsDataStore.edit { prefs -> prefs[ONBOARDING_COMPLETED] = true }
    }

    override fun observeThemeMode(): Flow<ThemeMode> =
        context.settingsDataStore.data.map { prefs -> prefs.toThemeMode() }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs -> prefs[THEME_MODE] = mode.name }
    }

    override fun observeNavBarStyle(): Flow<NavBarStyle> =
        context.settingsDataStore.data.map { prefs -> prefs.toNavBarStyle() }

    override suspend fun setNavBarStyle(style: NavBarStyle) {
        context.settingsDataStore.edit { prefs -> prefs[NAV_BAR_STYLE] = style.name }
    }

    override fun observeNavBarCornerRadius(): Flow<Int> =
        context.settingsDataStore.data.map { prefs -> prefs.navBarCornerRadius() }

    override suspend fun setNavBarCornerRadius(radius: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[NAV_BAR_CORNER_RADIUS] = radius.coerceIn(MIN_NAV_BAR_CORNER_RADIUS, MAX_NAV_BAR_CORNER_RADIUS)
        }
    }

    override fun observeNavBarCompactMode(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs.navBarCompactMode() }

    override suspend fun setNavBarCompactMode(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[NAV_BAR_COMPACT_MODE] = enabled }
    }

    override fun observeUseSmoothCorners(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs.useSmoothCorners() }

    override suspend fun setUseSmoothCorners(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[USE_SMOOTH_CORNERS] = enabled }
    }

    override fun observeDisableBlur(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs.disableBlur() }

    override suspend fun setDisableBlur(disabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[DISABLE_BLUR] = disabled }
    }

    override fun observeDefaultLaunchTab(): Flow<LaunchTab> =
        context.settingsDataStore.data.map { prefs -> prefs.toLaunchTab() }

    override suspend fun setDefaultLaunchTab(tab: LaunchTab) {
        context.settingsDataStore.edit { prefs -> prefs[DEFAULT_LAUNCH_TAB] = tab.name }
    }

    private fun Preferences.toSnapshot() =
        SettingsSnapshot(
            onboardingCompleted = this[ONBOARDING_COMPLETED] ?: false,
            automaticDownloads = automaticDownloads(),
            downloadOnMobileData = downloadOnMobileData(),
            themeMode = toThemeMode(),
            navBarStyle = toNavBarStyle(),
            navBarCornerRadius = navBarCornerRadius(),
            navBarCompactMode = navBarCompactMode(),
            useSmoothCorners = useSmoothCorners(),
            disableBlur = disableBlur(),
            defaultLaunchTab = toLaunchTab(),
        )

    // each preference's default lives here once, shared by its flow and the snapshot
    private fun Preferences.automaticDownloads() = this[AUTOMATIC_DOWNLOADS] ?: true

    private fun Preferences.downloadOnMobileData() = this[DOWNLOAD_ON_MOBILE_DATA] ?: false

    private fun Preferences.navBarCornerRadius() =
        (this[NAV_BAR_CORNER_RADIUS] ?: DEFAULT_NAV_BAR_CORNER_RADIUS)
            .coerceIn(MIN_NAV_BAR_CORNER_RADIUS, MAX_NAV_BAR_CORNER_RADIUS)

    private fun Preferences.navBarCompactMode() = this[NAV_BAR_COMPACT_MODE] ?: false

    private fun Preferences.useSmoothCorners() = this[USE_SMOOTH_CORNERS] ?: true

    private fun Preferences.disableBlur() = this[DISABLE_BLUR] ?: false

    private fun Preferences.toThemeMode(): ThemeMode =
        this[THEME_MODE]?.let { stored -> runCatching { ThemeMode.valueOf(stored) }.getOrNull() } ?: ThemeMode.SYSTEM

    private fun Preferences.toNavBarStyle(): NavBarStyle =
        this[NAV_BAR_STYLE]?.let { stored -> runCatching { NavBarStyle.valueOf(stored) }.getOrNull() }
            ?: NavBarStyle.FLOATING_PILL

    private fun Preferences.toLaunchTab(): LaunchTab =
        this[DEFAULT_LAUNCH_TAB]?.let { stored -> runCatching { LaunchTab.valueOf(stored) }.getOrNull() }
            ?: LaunchTab.HOME

    private companion object {
        // default ON per section 42.4 of the spec
        val AUTOMATIC_DOWNLOADS = booleanPreferencesKey("automatic_downloads")
        val DOWNLOAD_ON_MOBILE_DATA = booleanPreferencesKey("download_on_mobile_data")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NAV_BAR_STYLE = stringPreferencesKey("nav_bar_style")
        val NAV_BAR_CORNER_RADIUS = intPreferencesKey("nav_bar_corner_radius")
        val NAV_BAR_COMPACT_MODE = booleanPreferencesKey("nav_bar_compact_mode")
        val USE_SMOOTH_CORNERS = booleanPreferencesKey("use_smooth_corners")
        val DISABLE_BLUR = booleanPreferencesKey("disable_blur")
        val DEFAULT_LAUNCH_TAB = stringPreferencesKey("default_launch_tab")
    }
}
