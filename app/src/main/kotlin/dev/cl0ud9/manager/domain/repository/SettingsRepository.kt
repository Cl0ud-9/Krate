package dev.cl0ud9.manager.domain.repository

import dev.cl0ud9.manager.domain.model.LaunchTab
import dev.cl0ud9.manager.domain.model.NavBarStyle
import dev.cl0ud9.manager.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

// user-controlled preferences, section 42.4 of the spec - one cohesive preferences surface rather
// than split by feature, matching how the single DataStore-backed implementation stores them
@Suppress("TooManyFunctions")
interface SettingsRepository {
    fun observeAutomaticDownloads(): Flow<Boolean>

    suspend fun setAutomaticDownloads(enabled: Boolean)

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

    // squircle corners (ShapeCache) app-wide vs plain rounded corners
    fun observeUseSmoothCorners(): Flow<Boolean>

    suspend fun setUseSmoothCorners(enabled: Boolean)

    // skips the App Details/Settings push depth blur - cheaper on low-end devices
    fun observeDisableBlur(): Flow<Boolean>

    suspend fun setDisableBlur(disabled: Boolean)

    fun observeDefaultLaunchTab(): Flow<LaunchTab>

    suspend fun setDefaultLaunchTab(tab: LaunchTab)
}
