package dev.cl0ud9.manager.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.cl0ud9.manager.domain.model.LaunchTab
import dev.cl0ud9.manager.domain.model.NavBarStyle
import dev.cl0ud9.manager.domain.model.ThemeMode
import dev.cl0ud9.manager.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

// a 0-60dp range with a 32dp default, from sharp corners to a full pill
const val MIN_NAV_BAR_CORNER_RADIUS = 0
const val MAX_NAV_BAR_CORNER_RADIUS = 60
const val DEFAULT_NAV_BAR_CORNER_RADIUS = 32

@Suppress("TooManyFunctions")
class DataStoreSettingsRepository(
    private val context: Context,
) : SettingsRepository {
    override fun observeAutomaticDownloads(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[AUTOMATIC_DOWNLOADS] ?: true }

    override suspend fun setAutomaticDownloads(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[AUTOMATIC_DOWNLOADS] = enabled }
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
        context.settingsDataStore.data.map { prefs ->
            (prefs[NAV_BAR_CORNER_RADIUS] ?: DEFAULT_NAV_BAR_CORNER_RADIUS)
                .coerceIn(MIN_NAV_BAR_CORNER_RADIUS, MAX_NAV_BAR_CORNER_RADIUS)
        }

    override suspend fun setNavBarCornerRadius(radius: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[NAV_BAR_CORNER_RADIUS] = radius.coerceIn(MIN_NAV_BAR_CORNER_RADIUS, MAX_NAV_BAR_CORNER_RADIUS)
        }
    }

    override fun observeNavBarCompactMode(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[NAV_BAR_COMPACT_MODE] ?: false }

    override suspend fun setNavBarCompactMode(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[NAV_BAR_COMPACT_MODE] = enabled }
    }

    override fun observeUseSmoothCorners(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[USE_SMOOTH_CORNERS] ?: true }

    override suspend fun setUseSmoothCorners(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[USE_SMOOTH_CORNERS] = enabled }
    }

    override fun observeDisableBlur(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[DISABLE_BLUR] ?: false }

    override suspend fun setDisableBlur(disabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[DISABLE_BLUR] = disabled }
    }

    override fun observeDefaultLaunchTab(): Flow<LaunchTab> =
        context.settingsDataStore.data.map { prefs -> prefs.toLaunchTab() }

    override suspend fun setDefaultLaunchTab(tab: LaunchTab) {
        context.settingsDataStore.edit { prefs -> prefs[DEFAULT_LAUNCH_TAB] = tab.name }
    }

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
