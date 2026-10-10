package dev.cl0ud9.krate.ui.settings

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.ui.components.HeaderButtonColors
import dev.cl0ud9.krate.ui.components.HeaderIconButton

// every top-level tab carries this in its own action cluster - the one way into Settings now that
// it's off the bottom nav bar
@Composable
fun SettingsShortcutAction(onClick: () -> Unit) {
    HeaderIconButton(
        onClick = onClick,
        colors =
            HeaderButtonColors(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    ) {
        Icon(painterResource(R.drawable.ic_nav_settings_filled), contentDescription = "Settings")
    }
}
