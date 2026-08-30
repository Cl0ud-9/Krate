package dev.cl0ud9.manager.ui.navigation

import androidx.annotation.StringRes
import dev.cl0ud9.manager.R

// top-level bottom nav destinations, section 30 of the spec
enum class ManagerDestination(
    val route: String,
    @StringRes val labelRes: Int,
) {
    HOME("home", R.string.nav_home),
    APPS("apps", R.string.nav_apps),
    UPDATES("updates", R.string.nav_updates),
    SETTINGS("settings", R.string.nav_settings),
}
