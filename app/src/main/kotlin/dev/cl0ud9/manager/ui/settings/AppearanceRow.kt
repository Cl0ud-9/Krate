package dev.cl0ud9.manager.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.domain.model.NavBarStyle
import dev.cl0ud9.manager.domain.model.ThemeMode

// a real, stored preference (data/settings/DataStoreSettingsRepository.kt), not a decorative
// toggle - the same choices an onboarding preview would offer (choose a theme,
// choose a nav bar shape), but living here as an ordinary Settings row instead of a one-time wizard step
@Composable
internal fun AppearanceRow(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    navBarStyle: NavBarStyle,
    onNavBarStyleChange: (NavBarStyle) -> Unit,
) {
    SettingsRow(
        header =
            SettingsRowHeader(
                icon = Icons.Filled.Palette,
                title = "Appearance",
                subtitle = "Theme and navigation bar style",
                colors =
                    SettingsRowColors(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            ),
    ) {
        ThemeModeSelector(selected = themeMode, onSelect = onThemeModeChange)
        NavBarStyleSelector(selected = navBarStyle, onSelect = onNavBarStyleChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectorLabel("Theme")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                    label = { Text(mode.displayName()) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavBarStyleSelector(
    selected: NavBarStyle,
    onSelect: (NavBarStyle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectorLabel("Navigation bar")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            NavBarStyle.entries.forEachIndexed { index, style ->
                SegmentedButton(
                    selected = selected == style,
                    onClick = { onSelect(style) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = NavBarStyle.entries.size),
                    label = { Text(style.displayName()) },
                )
            }
        }
    }
}

@Composable
private fun SelectorLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun ThemeMode.displayName(): String =
    when (this) {
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
        ThemeMode.SYSTEM -> "System"
    }

private fun NavBarStyle.displayName(): String =
    when (this) {
        NavBarStyle.FLOATING_PILL -> "Pill"
        NavBarStyle.FULL_WIDTH -> "Full width"
    }
