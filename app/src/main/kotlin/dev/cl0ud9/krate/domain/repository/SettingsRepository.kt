package dev.cl0ud9.krate.domain.repository

import dev.cl0ud9.krate.domain.model.LaunchTab
import dev.cl0ud9.krate.domain.model.NavBarStyle
import dev.cl0ud9.krate.domain.model.SettingsSnapshot
import dev.cl0ud9.krate.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

// user-controlled preferences, section 42.4 of the spec - one cohesive preferences surface rather
// than split by feature, matching how the single DataStore-backed implementation stores them
@Suppress("TooManyFunctions")
interface SettingsRepository {
    // the settings as last read, or null before the first read; screens start from it so nothing animates in
    fun currentSettings(): SettingsSnapshot?

    fun observeAutomaticDownloads(): Flow<Boolean>

    suspend fun setAutomaticDownloads(enabled: Boolean)

    // lets automatic downloads use mobile data too, not just Wi-Fi; off unless the user opts in
    fun observeDownloadOnMobileData(): Flow<Boolean>

    suspend fun setDownloadOnMobileData(enabled: Boolean)

    // whether first-run onboarding (amendment 44.4) has been completed - gates the Apps catalog
    fun observeOnboardingCompleted(): Flow<Boolean>

    suspend fun setOnboardingCompleted()

    // Settings > Appearance - added so the theme/nav-bar-style toggles are real, stored preferences
    // rather than decorative controls
    fun observeThemeMode(): Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)

    fun observeNavBarStyle(): Flow<NavBarStyle>

    suspend fun setNavBarStyle(style: NavBarStyle)

    // 0-60dp, only meaningful for NavBarStyle.FLOATING_PILL
    fun observeNavBarCornerRadius(): Flow<Int>

    suspend fun setNavBarCornerRadius(radius: Int)

    // a shorter bar with icon-only items, no labels
    fun observeNavBarCompactMode(): Flow<Boolean>

    suspend fun setNavBarCompactMode(enabled: Boolean)

    fun observeDefaultLaunchTab(): Flow<LaunchTab>

    suspend fun setDefaultLaunchTab(tab: LaunchTab)
}
