package dev.cl0ud9.krate.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.NavBarStyle
import dev.cl0ud9.krate.platform.autoupdate.autoUpdatesSupported
import dev.cl0ud9.krate.ui.components.KrateSwitch
import dev.cl0ud9.krate.ui.navigation.liquidGlass
import dev.cl0ud9.krate.ui.navigation.liquidGlassSupported
import dev.cl0ud9.krate.ui.settings.SettingsViewModel
import dev.cl0ud9.krate.ui.settings.ThemeModeCards
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.isShortScreen

@Composable
internal fun ThemePage(viewModel: SettingsViewModel) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    CustomizePageLayout(title = "App theme", description = "Pick the look you want before you start exploring.") {
        ThemeModeCards(selected = themeMode, onSelect = viewModel::setThemeMode)
    }
}

@Composable
internal fun NavigationPage(
    viewModel: SettingsViewModel,
    onCustomizeRadius: () -> Unit,
) {
    val style by viewModel.navBarStyle.collectAsStateWithLifecycle()
    val radius by viewModel.navBarCornerRadius.collectAsStateWithLifecycle()
    val glass by viewModel.liquidGlass.collectAsStateWithLifecycle()
    val pill = style == NavBarStyle.FLOATING_PILL
    CustomizePageLayout(
        title = "Navigation bar",
        description = "Choose how the bar at the bottom looks, and whether Krate turns to glass.",
    ) {
        NavBarPreview(pill = pill, radius = radius, glass = glass && liquidGlassSupported)
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeCache.rounded28,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                SwitchLine(
                    title = "Floating pill",
                    subtitle = if (pill) "A floating bar with rounded corners" else "A full-width bar along the bottom",
                    checked = pill,
                    onCheckedChange = {
                        viewModel.setNavBarStyle(
                            if (it) NavBarStyle.FLOATING_PILL else NavBarStyle.FULL_WIDTH,
                        )
                    },
                )
                // only where the phone can draw it; anywhere else the page is as it always was
                if (liquidGlassSupported) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SwitchLine(
                        title = "Liquid glass",
                        subtitle = "Frosted, see-through materials across the app, tinted by your colours",
                        checked = glass,
                        onCheckedChange = viewModel::setLiquidGlass,
                    )
                }
                // the gap above the button folds with it rather than snapping shut; timed with the preview
                AnimatedVisibility(
                    visible = pill,
                    enter = expandVertically(tween(PREVIEW_MORPH_MS)) + fadeIn(tween(PREVIEW_MORPH_MS)),
                    exit = shrinkVertically(tween(PREVIEW_MORPH_MS)) + fadeOut(tween(PREVIEW_MORPH_MS)),
                ) {
                    FilledTonalButton(
                        onClick = onCustomizeRadius,
                        modifier = Modifier.padding(top = 14.dp).fillMaxWidth(),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_rounded_corner_rounded),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Customize corner radius")
                    }
                }
            }
        }
    }
}

@Composable
internal fun UpdatesPage(viewModel: SettingsViewModel) {
    val automatic by viewModel.automaticDownloads.collectAsStateWithLifecycle()
    val autoInstall by viewModel.autoInstallUpdates.collectAsStateWithLifecycle()
    StepPageLayout(
        title = "Staying up to date",
        description = "Krate checks for new versions in the background and tells you when something's waiting.",
        icons = UPDATES_ICONS,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeCache.rounded28,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SwitchLine(
                    title = "Download automatically",
                    subtitle = "On Wi-Fi, so updates are ready to install in seconds.",
                    checked = automatic,
                    onCheckedChange = viewModel::setAutomaticDownloads,
                )
                // Android 12 is the first that lets Krate update an app without asking, so earlier it isn't offered
                if (autoUpdatesSupported) {
                    SwitchLine(
                        title = "Install them automatically",
                        subtitle =
                            if (automatic) {
                                "Updates to apps from Krate install a day after release, while you're not using " +
                                    "them. It never rolls an app back or erases its data."
                            } else {
                                "Turn on Download automatically first. It fetches the updates this installs."
                            },
                        checked = autoInstall && automatic,
                        onCheckedChange = viewModel::setAutoInstallUpdates,
                        enabled = automatic,
                    )
                }
            }
        }
    }
}

// title and explanation up top, the page's choices centered below, and a note that it can all change later;
// on a phone on its side the words sit left and the choices scroll on the right
@Composable
private fun CustomizePageLayout(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    if (isShortScreen()) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CustomizeTitle(title, TextAlign.Start)
                CustomizeDescription(description, TextAlign.Start)
                ChangeLaterNote(TextAlign.Start)
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                content()
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(16.dp))
        CustomizeTitle(title, TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        CustomizeDescription(description, TextAlign.Center)
        Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) { content() }
        ChangeLaterNote(TextAlign.Center, Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun CustomizeTitle(
    title: String,
    align: TextAlign,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.displayMedium.copy(fontSize = 32.sp, lineHeight = 40.sp),
        textAlign = align,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CustomizeDescription(
    description: String,
    align: TextAlign,
) {
    Text(
        text = description,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = align,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChangeLaterNote(
    align: TextAlign,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "You can change this later in Settings > Appearance.",
        style = MaterialTheme.typography.bodySmall,
        textAlign = align,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchLine(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        KrateSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
