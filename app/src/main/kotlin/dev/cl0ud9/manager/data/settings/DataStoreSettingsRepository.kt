package dev.cl0ud9.manager.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.cl0ud9.manager.domain.model.NavBarStyle
import dev.cl0ud9.manager.domain.model.ThemeMode
import dev.cl0ud9.manager.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

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

    private fun Preferences.toThemeMode(): ThemeMode =
        this[THEME_MODE]?.let { stored -> runCatching { ThemeMode.valueOf(stored) }.getOrNull() } ?: ThemeMode.SYSTEM

    private fun Preferences.toNavBarStyle(): NavBarStyle =
        this[NAV_BAR_STYLE]?.let { stored -> runCatching { NavBarStyle.valueOf(stored) }.getOrNull() }
            ?: NavBarStyle.FLOATING_PILL

    private companion object {
        // default ON per section 42.4 of the spec
        val AUTOMATIC_DOWNLOADS = booleanPreferencesKey("automatic_downloads")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NAV_BAR_STYLE = stringPreferencesKey("nav_bar_style")
    }
}
