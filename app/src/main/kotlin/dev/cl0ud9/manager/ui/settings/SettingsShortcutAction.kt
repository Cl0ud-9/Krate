package dev.cl0ud9.manager.ui.settings

import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import dev.cl0ud9.manager.R

// every top-level tab carries this in its own action cluster - the one way into Settings now that
// it's off the bottom nav bar
@Composable
fun SettingsShortcutAction(onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    ) {
        Icon(painterResource(R.drawable.ic_nav_settings_filled), contentDescription = "Settings")
    }
}
