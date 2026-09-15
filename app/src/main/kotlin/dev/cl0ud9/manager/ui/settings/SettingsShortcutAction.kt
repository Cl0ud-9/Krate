package dev.cl0ud9.manager.ui.settings

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import dev.cl0ud9.manager.R

// every top-level tab carries this in its own action cluster - the one way into Settings now that
// it's off the bottom nav bar
@Composable
fun SettingsShortcutAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(R.drawable.ic_nav_settings_outline), contentDescription = "Settings")
    }
}
