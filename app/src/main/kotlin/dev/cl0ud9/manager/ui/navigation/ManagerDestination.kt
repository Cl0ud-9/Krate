package dev.cl0ud9.manager.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.ui.graphics.vector.ImageVector
import dev.cl0ud9.manager.R

// top-level bottom nav destinations, section 30 of the spec
enum class ManagerDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home),
    APPS("apps", R.string.nav_apps, Icons.Filled.Apps),
    UPDATES("updates", R.string.nav_updates, Icons.Filled.Update),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
}
