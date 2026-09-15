package dev.cl0ud9.manager.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

// a navigation row into its own screen now, not an inline expandable - it outgrew a single row's
// worth of controls once nav bar shape/blur/launch-tab settings joined the original theme picker
@Composable
internal fun AppearanceRow(
    shape: Shape,
    onClick: () -> Unit,
) {
    SettingsRow(
        header =
            SettingsRowHeader(
                icon = Icons.Filled.Palette,
                title = "Appearance",
                subtitle = "Theme, navigation bar, effects",
                colors =
                    SettingsRowColors(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            ),
        shape = shape,
        onClick = onClick,
        trailing = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
    )
}
