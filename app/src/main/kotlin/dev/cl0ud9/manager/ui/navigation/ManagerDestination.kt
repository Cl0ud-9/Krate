package dev.cl0ud9.manager.ui.navigation

import androidx.annotation.StringRes
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.domain.model.LaunchTab

// top-level bottom nav destinations, section 30 of the spec
// selectedIcon/unselectedIcon (filled vs outline) per M3 nav bar guidelines, both drawn from the
// same Material Symbols Rounded icon family (see NavIcon.kt) so the selected state's fill change
// reads as a weight/fill shift within one consistent icon style, not a swap between two families
enum class ManagerDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @StringRes val titleRes: Int,
    val selectedIcon: NavIcon,
    val unselectedIcon: NavIcon,
) {
    HOME(
        "home",
        R.string.nav_home,
        R.string.app_name,
        NavIcon.Drawable(R.drawable.ic_nav_home_filled),
        NavIcon.Drawable(R.drawable.ic_nav_home_outline),
    ),
    APPS(
        "apps",
        R.string.nav_apps,
        R.string.nav_apps,
        NavIcon.Drawable(R.drawable.ic_nav_apps_filled),
        NavIcon.Drawable(R.drawable.ic_nav_apps_outline),
    ),
    UPDATES(
        "updates",
        R.string.nav_updates,
        R.string.nav_updates,
        NavIcon.Drawable(R.drawable.ic_nav_update_filled),
        NavIcon.Drawable(R.drawable.ic_nav_update_outline),
    ),
    SETTINGS(
        "settings",
        R.string.nav_settings,
        R.string.nav_settings,
        NavIcon.Drawable(R.drawable.ic_nav_settings_filled),
        NavIcon.Drawable(R.drawable.ic_nav_settings_outline),
    ),
}

// the bottom nav bar only shows these three - Settings is reached through a shortcut on each of
// their own top bars instead, not through a fourth bottom tab
val ManagerBottomNavDestinations = ManagerDestination.entries.filter { it != ManagerDestination.SETTINGS }

// Settings > Appearance's default-launch-tab picker only offers the three real bottom-nav tabs
fun LaunchTab.toRoute(): String =
    when (this) {
        LaunchTab.HOME -> ManagerDestination.HOME.route
        LaunchTab.APPS -> ManagerDestination.APPS.route
        LaunchTab.UPDATES -> ManagerDestination.UPDATES.route
    }
