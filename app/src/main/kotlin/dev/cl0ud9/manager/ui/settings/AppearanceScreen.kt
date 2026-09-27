package dev.cl0ud9.manager.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.domain.model.LaunchTab
import dev.cl0ud9.manager.domain.model.NavBarStyle
import dev.cl0ud9.manager.domain.model.ThemeMode

// which choice sheet is open, if any
private enum class AppearanceSheet { THEME, NAV_STYLE, LAUNCH_TAB }

private const val ROWS = 4
private const val FOLD_MS = 300

// Settings > Appearance: theme, the navigation bar and the app's corners, each row showing its current value
@Composable
fun AppearanceRoute(
    scrollState: ScrollState,
    topContentPadding: Dp,
    onOpenCornerRadius: () -> Unit,
) {
    val viewModel = rememberSettingsViewModel()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val navBarStyle by viewModel.navBarStyle.collectAsStateWithLifecycle()
    var openSheet by rememberSaveable { mutableStateOf<AppearanceSheet?>(null) }
    SettingsPage(scrollState, topContentPadding) {
        SettingsSectionLabel("Theme", first = true)
        SettingValueRow(
            item =
                SettingItem(
                    painterResource(R.drawable.ic_dark_mode_rounded),
                    "App theme",
                    "Light, dark, or match your phone.",
                ),
            value = themeMode.displayName(),
            shape = settingsGroupShape(0, 1),
            onClick = { openSheet = AppearanceSheet.THEME },
        )
        SettingsSectionLabel("Navigation")
        NavigationRows(viewModel = viewModel, navBarStyle = navBarStyle, onOpenSheet = {
            openSheet = it
        }, onOpenCornerRadius = onOpenCornerRadius)
        SettingsSectionLabel("Effects")
        val useSmoothCorners by viewModel.useSmoothCorners.collectAsStateWithLifecycle()
        SettingSwitchRow(
            item =
                SettingItem(
                    painterResource(R.drawable.ic_shapes_rounded),
                    "Smooth corners",
                    "Squircle shapes across the app. Off is lighter on older phones.",
                ),
            checked = useSmoothCorners,
            shape = settingsGroupShape(0, 1),
            onCheckedChange = viewModel::setUseSmoothCorners,
        )
        SettingsSectionLabel("Launcher")
        LauncherIconRow()
    }
    AppearanceSheets(openSheet = openSheet, viewModel = viewModel, onDismiss = { openSheet = null })
}

@Composable
private fun NavigationRows(
    viewModel: SettingsViewModel,
    navBarStyle: NavBarStyle,
    onOpenSheet: (AppearanceSheet) -> Unit,
    onOpenCornerRadius: () -> Unit,
) {
    val cornerRadius by viewModel.navBarCornerRadius.collectAsStateWithLifecycle()
    val compactMode by viewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val launchTab by viewModel.defaultLaunchTab.collectAsStateWithLifecycle()
    val pill = navBarStyle == NavBarStyle.FLOATING_PILL
    // four fixed slots, so the pill-only rows fold away (their 2dp gap with them) without the others reshaping
    Column {
        SettingValueRow(
            item =
                SettingItem(
                    painterResource(R.drawable.ic_call_to_action_rounded),
                    "Navigation bar",
                    "A floating pill or a full-width bar.",
                ),
            value = navBarStyle.displayName(),
            shape = settingsGroupShape(0, ROWS),
            onClick = { onOpenSheet(AppearanceSheet.NAV_STYLE) },
        )
        AnimatedVisibility(
            visible = pill,
            enter = expandVertically(tween(FOLD_MS)) + fadeIn(tween(FOLD_MS)),
            exit = shrinkVertically(tween(FOLD_MS)) + fadeOut(tween(FOLD_MS)),
        ) {
            Column(modifier = Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingValueRow(
                    item =
                        SettingItem(
                            painterResource(R.drawable.ic_rounded_corner_rounded),
                            "Corner radius",
                            "Match the bar to your screen's own corners.",
                        ),
                    value = "$cornerRadius dp",
                    shape = settingsGroupShape(1, ROWS),
                    onClick = onOpenCornerRadius,
                )
                SettingSwitchRow(
                    item =
                        SettingItem(
                            painterResource(R.drawable.ic_density_medium_rounded),
                            "Compact mode",
                            "A shorter bar with icon-only tabs.",
                        ),
                    checked = compactMode,
                    shape = settingsGroupShape(2, ROWS),
                    onCheckedChange = viewModel::setNavBarCompactMode,
                )
            }
        }
    }
    SettingValueRow(
        item = SettingItem(painterResource(R.drawable.ic_tab_rounded), "Open on", "The tab Krate starts on."),
        value = launchTab.displayName(),
        shape = settingsGroupShape(ROWS - 1, ROWS),
        onClick = { onOpenSheet(AppearanceSheet.LAUNCH_TAB) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSheets(
    openSheet: AppearanceSheet?,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    when (openSheet) {
        AppearanceSheet.THEME -> {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    text = "App theme",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                ThemeModeCards(
                    selected = themeMode,
                    onSelect = {
                        viewModel.setThemeMode(it)
                        onDismiss()
                    },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                )
            }
        }

        AppearanceSheet.NAV_STYLE -> {
            val style by viewModel.navBarStyle.collectAsStateWithLifecycle()
            SettingOptionSheet(
                title = "Navigation bar",
                options =
                    NavBarStyle.entries.map {
                        SettingOption(it, painterResource(R.drawable.ic_call_to_action_rounded), it.displayName())
                    },
                selected = style,
                onSelect = viewModel::setNavBarStyle,
                onDismiss = onDismiss,
            )
        }

        AppearanceSheet.LAUNCH_TAB -> {
            val tab by viewModel.defaultLaunchTab.collectAsStateWithLifecycle()
            SettingOptionSheet(
                title = "Open on",
                options = LaunchTab.entries.map { SettingOption(it, painterResource(it.iconRes()), it.displayName()) },
                selected = tab,
                onSelect = viewModel::setDefaultLaunchTab,
                onDismiss = onDismiss,
            )
        }

        null -> Unit
    }
}

internal fun ThemeMode.displayName(): String =
    when (this) {
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
        ThemeMode.SYSTEM -> "Follow system"
    }

private fun NavBarStyle.displayName(): String =
    when (this) {
        NavBarStyle.FLOATING_PILL -> "Floating pill"
        NavBarStyle.FULL_WIDTH -> "Full width"
    }

private fun LaunchTab.displayName(): String =
    when (this) {
        LaunchTab.HOME -> "Home"
        LaunchTab.APPS -> "Apps"
        LaunchTab.UPDATES -> "Updates"
    }

private fun LaunchTab.iconRes(): Int =
    when (this) {
        LaunchTab.HOME -> R.drawable.ic_nav_home_filled
        LaunchTab.APPS -> R.drawable.ic_nav_apps_filled
        LaunchTab.UPDATES -> R.drawable.ic_nav_update_filled
    }
