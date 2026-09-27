package dev.cl0ud9.krate.domain.model

// every stored preference at one moment, so a screen can open on the real values instead of defaults
data class SettingsSnapshot(
    val onboardingCompleted: Boolean,
    val automaticDownloads: Boolean,
    val downloadOnMobileData: Boolean,
    val themeMode: ThemeMode,
    val navBarStyle: NavBarStyle,
    val navBarCornerRadius: Int,
    val navBarCompactMode: Boolean,
    val useSmoothCorners: Boolean,
    val disableBlur: Boolean,
    val defaultLaunchTab: LaunchTab,
)
