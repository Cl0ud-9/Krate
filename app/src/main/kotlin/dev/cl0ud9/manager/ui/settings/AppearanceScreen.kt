@file:Suppress("TooManyFunctions")

package dev.cl0ud9.manager.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.manager.data.settings.MAX_NAV_BAR_CORNER_RADIUS
import dev.cl0ud9.manager.data.settings.MIN_NAV_BAR_CORNER_RADIUS
import dev.cl0ud9.manager.domain.model.LaunchTab
import dev.cl0ud9.manager.domain.model.NavBarStyle
import dev.cl0ud9.manager.domain.model.ThemeMode
import dev.cl0ud9.manager.ui.components.ManagerSwitch
import dev.cl0ud9.manager.ui.navigation.DetailContentTopGap

// the nav-graph entry point: owns the ViewModel and state collection, then hands plain state +
// callbacks down to the stateless AppearanceScreen below. scrollState/topContentPadding come from
// the shared collapsing header this screen is hosted in
@Composable
fun AppearanceRoute(
    scrollState: ScrollState,
    topContentPadding: Dp,
) {
    val viewModel = rememberSettingsViewModel()
    AppearanceScreen(
        scrollState = scrollState,
        topContentPadding = topContentPadding,
        themeMode = viewModel.themeMode.collectAsStateWithLifecycle().value,
        onThemeModeChange = viewModel::setThemeMode,
        navBarStyle = viewModel.navBarStyle.collectAsStateWithLifecycle().value,
        onNavBarStyleChange = viewModel::setNavBarStyle,
        navBarCornerRadius = viewModel.navBarCornerRadius.collectAsStateWithLifecycle().value,
        onNavBarCornerRadiusChange = viewModel::setNavBarCornerRadius,
        navBarCompactMode = viewModel.navBarCompactMode.collectAsStateWithLifecycle().value,
        onNavBarCompactModeChange = viewModel::setNavBarCompactMode,
        useSmoothCorners = viewModel.useSmoothCorners.collectAsStateWithLifecycle().value,
        onUseSmoothCornersChange = viewModel::setUseSmoothCorners,
        disableBlur = viewModel.disableBlur.collectAsStateWithLifecycle().value,
        onDisableBlurChange = viewModel::setDisableBlur,
        defaultLaunchTab = viewModel.defaultLaunchTab.collectAsStateWithLifecycle().value,
        onDefaultLaunchTabChange = viewModel::setDefaultLaunchTab,
    )
}

// Settings > Appearance's own screen - pushed from a navigation row rather than expanding inline,
// since it outgrew a single row's worth of controls once the nav bar/shape/blur settings joined
// the original theme + nav-bar-style pair
@Suppress("LongParameterList")
@Composable
private fun AppearanceScreen(
    scrollState: ScrollState,
    topContentPadding: Dp,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    navBarStyle: NavBarStyle,
    onNavBarStyleChange: (NavBarStyle) -> Unit,
    navBarCornerRadius: Int,
    onNavBarCornerRadiusChange: (Int) -> Unit,
    navBarCompactMode: Boolean,
    onNavBarCompactModeChange: (Boolean) -> Unit,
    useSmoothCorners: Boolean,
    onUseSmoothCornersChange: (Boolean) -> Unit,
    disableBlur: Boolean,
    onDisableBlurChange: (Boolean) -> Unit,
    defaultLaunchTab: LaunchTab,
    onDefaultLaunchTabChange: (LaunchTab) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    top = topContentPadding + DetailContentTopGap,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp,
                ),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        AppearanceSection(title = "Theme") {
            ThemeModeSelector(selected = themeMode, onSelect = onThemeModeChange)
        }
        AppearanceSection(title = "Navigation bar") {
            NavBarStyleSelector(selected = navBarStyle, onSelect = onNavBarStyleChange)
            if (navBarStyle == NavBarStyle.FLOATING_PILL) {
                CornerRadiusSlider(value = navBarCornerRadius, onValueChange = onNavBarCornerRadiusChange)
                SwitchRow(
                    label = "Compact mode",
                    subtitle = "A shorter bar with icon-only tabs",
                    checked = navBarCompactMode,
                    onCheckedChange = onNavBarCompactModeChange,
                )
            }
        }
        AppearanceSection(title = "App navigation") {
            LaunchTabSelector(selected = defaultLaunchTab, onSelect = onDefaultLaunchTabChange)
        }
        AppearanceSection(title = "Effects") {
            SwitchRow(
                label = "Smooth corners",
                subtitle = "Squircle shapes throughout the app, instead of plain rounded corners",
                checked = useSmoothCorners,
                onCheckedChange = onUseSmoothCornersChange,
            )
            SwitchRow(
                label = "Disable blur",
                subtitle = "Skip the backdrop blur when a screen is pushed over another - cheaper on low-end devices",
                checked = disableBlur,
                onCheckedChange = onDisableBlurChange,
            )
        }
    }
}

@Composable
private fun AppearanceSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = { content() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectorLabel("Appearance")
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
        SelectorLabel("Style")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LaunchTabSelector(
    selected: LaunchTab,
    onSelect: (LaunchTab) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectorLabel("Default tab on launch")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            LaunchTab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = selected == tab,
                    onClick = { onSelect(tab) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = LaunchTab.entries.size),
                    label = { Text(tab.displayName()) },
                )
            }
        }
    }
}

@Composable
private fun CornerRadiusSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SelectorLabel("Corner radius")
            Text(text = "${value}dp", style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = MIN_NAV_BAR_CORNER_RADIUS.toFloat()..MAX_NAV_BAR_CORNER_RADIUS.toFloat(),
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ManagerSwitch(checked = checked, onCheckedChange = onCheckedChange)
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

private fun LaunchTab.displayName(): String =
    when (this) {
        LaunchTab.HOME -> "Home"
        LaunchTab.APPS -> "Apps"
        LaunchTab.UPDATES -> "Updates"
    }
