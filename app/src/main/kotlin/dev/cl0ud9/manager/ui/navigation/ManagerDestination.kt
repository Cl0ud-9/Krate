package dev.cl0ud9.manager.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Update
import androidx.compose.ui.graphics.vector.ImageVector
import dev.cl0ud9.manager.R

// top-level bottom nav destinations, section 30 of the spec
// selectedIcon/unselectedIcon (filled vs outlined) per M3 nav bar guidelines - a single glyph with
// only a tint change was the one real gap found in an M3 nav bar audit against the current spec
enum class ManagerDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @StringRes val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME("home", R.string.nav_home, R.string.app_name, Icons.Filled.Home, Icons.Outlined.Home),
    APPS("apps", R.string.nav_apps, R.string.nav_apps, Icons.Filled.Apps, Icons.Outlined.Apps),
    UPDATES("updates", R.string.nav_updates, R.string.nav_updates, Icons.Filled.Update, Icons.Outlined.Update),
    SETTINGS("settings", R.string.nav_settings, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}
