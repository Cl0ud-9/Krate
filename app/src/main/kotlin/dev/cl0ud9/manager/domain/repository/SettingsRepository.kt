package dev.cl0ud9.manager.domain.repository

import dev.cl0ud9.manager.domain.model.NavBarStyle
import dev.cl0ud9.manager.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

// user-controlled preferences, section 42.4 of the spec
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
}
