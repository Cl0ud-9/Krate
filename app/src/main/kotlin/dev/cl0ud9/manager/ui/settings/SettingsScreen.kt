package dev.cl0ud9.manager.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.platform.selfupdate.ManagerUpdateStatus
import dev.cl0ud9.manager.ui.apps.SuggestAppSheet
import dev.cl0ud9.manager.ui.components.ManagerSwitch
import dev.cl0ud9.manager.ui.util.DebouncedButtonState
import dev.cl0ud9.manager.ui.util.KOFI_URL
import dev.cl0ud9.manager.ui.util.rememberLastNonNull

// an index of categories, each opening its own page - a short list you can take in at a glance,
// grouped under section labels, instead of every control stacked on one long page. A waiting
// manager update gets a banner at the top that leads to About, where the Update button is
@Composable
fun SettingsScreen(
    scrollState: ScrollState,
    topContentPadding: Dp,
    onNavigate: (String) -> Unit,
) {
    val viewModel = rememberSettingsViewModel()
    val managerUpdateState by viewModel.managerUpdateState.collectAsStateWithLifecycle()
    val hasGitHubToken by viewModel.hasGitHubToken.collectAsStateWithLifecycle()
    val update = (managerUpdateState as? ManagerUpdateUiState.Result)?.status as? ManagerUpdateStatus.UpdateAvailable
    var showSuggest by rememberSaveable { mutableStateOf(false) }
    if (showSuggest) SuggestAppSheet(onDismiss = { showSuggest = false })
    SettingsPage(scrollState, topContentPadding) {
        // the banner keeps its version while folding away, and its space below goes with it, not in one jump
        val shownUpdate = rememberLastNonNull(update)
        Column {
            AnimatedVisibility(
                visible = update != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                shownUpdate?.let {
                    Column {
                        ManagerUpdateBanner(it.latestVersion) { onNavigate(SettingsPageRoute.ABOUT) }
                        Spacer(modifier = Modifier.height(BANNER_GAP))
                    }
                }
            }
            SettingsSectionLabel("General", first = true)
        }
        AppearanceRow(shape = settingsGroupShape(0, 2), onClick = { onNavigate(SettingsPageRoute.APPEARANCE) })
        SettingsNavRow(
            icon = painterResource(R.drawable.ic_update_rounded),
            title = "Downloads & storage",
            subtitle = "Automatic downloads, mobile data, download cache",
            colors = SettingsTint.GREEN.colors(),
            shape = settingsGroupShape(1, 2),
            onClick = { onNavigate(SettingsPageRoute.DOWNLOADS) },
        )
        SettingsSectionLabel("Account")
        SettingsNavRow(
            icon = painterResource(R.drawable.ic_key_rounded),
            title = "GitHub access",
            subtitle = if (hasGitHubToken) "Token saved" else "Needed for a few private apps",
            colors = SettingsTint.INDIGO.colors(),
            shape = settingsGroupShape(0, 1),
            onClick = { onNavigate(SettingsPageRoute.GITHUB) },
        )
        SupportSection(onNavigate = onNavigate, onSuggest = { showSuggest = true })
    }
}

private const val GROUP_ROWS = 2

// banner to "General": the same 22dp it had when the label carried the space itself
private val BANNER_GAP = 18.dp

// telling us things (problems, app ideas), then Krate itself (chipping in, and the About page), as two small groups
@Composable
private fun SupportSection(
    onNavigate: (String) -> Unit,
    onSuggest: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    SettingsSectionLabel("Feedback")
    SettingsNavRow(
        icon = painterResource(R.drawable.ic_feedback_rounded),
        title = "Feedback & bug reports",
        subtitle = "Report a problem or share an idea",
        colors = SettingsTint.AMBER.colors(),
        shape = settingsGroupShape(0, GROUP_ROWS),
        onClick = { onNavigate(SettingsPageRoute.FEEDBACK) },
    )
    SettingsNavRow(
        icon = painterResource(R.drawable.ic_campaign_rounded),
        title = "Suggest an app",
        subtitle = "Know an app that belongs in Krate?",
        colors = SettingsTint.TEAL.colors(),
        shape = settingsGroupShape(1, GROUP_ROWS),
        onClick = onSuggest,
    )
    SettingsSectionLabel("Krate")
    SettingsNavRow(
        icon = painterResource(R.drawable.ic_coffee_rounded),
        title = "Support Krate",
        subtitle = "Krate runs on coffee. Top it up on Ko-fi?",
        colors = SettingsTint.ROSE.colors(),
        shape = settingsGroupShape(0, GROUP_ROWS),
        onClick = { runCatching { uriHandler.openUri(KOFI_URL) } },
    )
    SettingsNavRow(
        icon = painterResource(R.drawable.ic_krate),
        title = "About Krate",
        subtitle = "Version ${rememberVersionName()}, updates, the team",
        colors = SettingsTint.BLUE.colors(),
        shape = settingsGroupShape(1, GROUP_ROWS),
        onClick = { onNavigate(SettingsPageRoute.ABOUT) },
    )
}

// routes of the pages Settings' rows open
object SettingsPageRoute {
    const val APPEARANCE = "settings/appearance"
    const val DOWNLOADS = "settings/downloads"
    const val GITHUB = "settings/github"
    const val FEEDBACK = "settings/feedback"
    const val ABOUT = "settings/about"
}

@Composable
internal fun AutomaticDownloadsRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: Shape,
    onMobileData: Boolean = false,
) {
    val networks = if (onMobileData) "on Wi-Fi or mobile data" else "on Wi-Fi"
    SettingsRow(
        header =
            SettingsRowHeader(
                icon = painterResource(R.drawable.ic_update_rounded),
                title = "Automatic downloads",
                subtitle = "Download updates in the background $networks. Installing always needs your confirmation.",
                colors = SettingsTint.GREEN.colors(),
            ),
        shape = shape,
        trailing = { ManagerSwitch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

// lets automatic downloads use mobile data as well; only meaningful while those are on
@Composable
internal fun MobileDataDownloadsRow(
    checked: Boolean,
    available: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: Shape,
) {
    SettingsRow(
        header =
            SettingsRowHeader(
                icon = painterResource(R.drawable.ic_signal_cellular_alt_rounded),
                title = "Use mobile data too",
                subtitle =
                    if (available) {
                        "Some updates are over 100 MB, so this can use a lot of data. Skipped while roaming " +
                            "or with Data Saver on."
                    } else {
                        "Turn on Automatic downloads to use this."
                    },
                colors = SettingsTint.AMBER.colors(),
            ),
        shape = shape,
        trailing = {
            ManagerSwitch(checked = checked && available, onCheckedChange = onCheckedChange, enabled = available)
        },
    )
}

@Composable
internal fun StorageRow(
    cacheClearedMessage: String?,
    clearCacheState: DebouncedButtonState,
    shape: Shape,
) {
    SettingsRow(
        header =
            SettingsRowHeader(
                icon = painterResource(R.drawable.ic_delete_sweep_rounded),
                title = "Storage",
                subtitle = "Downloaded update files are deleted as soon as they're installed.",
                colors = SettingsTint.TEAL.colors(),
            ),
        shape = shape,
    ) {
        StorageRowContent(cacheClearedMessage = cacheClearedMessage, clearCacheState = clearCacheState)
    }
}

@Composable
internal fun rememberVersionName(): String {
    val context = LocalContext.current
    return remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "unknown"
    }
}

@Composable
private fun StorageRowContent(
    cacheClearedMessage: String?,
    clearCacheState: DebouncedButtonState,
) {
    FilledTonalButton(
        onClick = clearCacheState.onClick,
        enabled = clearCacheState.enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Clear download cache")
    }
    if (cacheClearedMessage != null) {
        Text(
            text = cacheClearedMessage,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}
