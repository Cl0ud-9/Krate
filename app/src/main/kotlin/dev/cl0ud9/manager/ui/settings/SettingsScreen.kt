package dev.cl0ud9.manager.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.platform.selfupdate.ManagerUpdateStatus
import dev.cl0ud9.manager.ui.components.ManagerSwitch
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.util.DebouncedButtonState

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
    SettingsPage(scrollState, topContentPadding) {
        if (update != null) ManagerUpdateBanner(update.latestVersion) { onNavigate(SettingsPageRoute.ABOUT) }
        SettingsSectionLabel("General", first = update == null)
        AppearanceRow(shape = settingsGroupShape(0, 2), onClick = { onNavigate(SettingsPageRoute.APPEARANCE) })
        SettingsNavRow(
            icon = painterResource(R.drawable.ic_update_rounded),
            title = "Downloads & storage",
            subtitle = "Automatic downloads, download cache",
            colors = defaultSettingsRowColors(),
            shape = settingsGroupShape(1, 2),
            onClick = { onNavigate(SettingsPageRoute.DOWNLOADS) },
        )
        SettingsSectionLabel("Account")
        SettingsNavRow(
            icon = painterResource(R.drawable.ic_key_rounded),
            title = "GitHub access",
            subtitle = if (hasGitHubToken) "Token saved" else "Needed for a few private apps",
            colors =
                SettingsRowColors(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            shape = settingsGroupShape(0, 1),
            onClick = { onNavigate(SettingsPageRoute.GITHUB) },
        )
        SettingsSectionLabel("Support")
        SettingsNavRow(
            icon = painterResource(R.drawable.ic_feedback_rounded),
            title = "Feedback & bug reports",
            subtitle = "Report a problem or suggest something",
            colors = defaultSettingsRowColors(),
            shape = settingsGroupShape(0, 2),
            onClick = { onNavigate(SettingsPageRoute.FEEDBACK) },
        )
        SettingsNavRow(
            icon = painterResource(R.drawable.ic_info_rounded),
            title = "About",
            subtitle = "Version ${rememberVersionName()}, updates, what's new",
            colors =
                SettingsRowColors(
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            shape = settingsGroupShape(1, 2),
            onClick = { onNavigate(SettingsPageRoute.ABOUT) },
        )
    }
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
) {
    SettingsRow(
        header =
            SettingsRowHeader(
                icon = painterResource(R.drawable.ic_update_rounded),
                title = "Automatic downloads",
                subtitle = "Download updates in the background on Wi-Fi. Installing always needs your confirmation.",
                colors = defaultSettingsRowColors(),
            ),
        shape = shape,
        trailing = { ManagerSwitch(checked = checked, onCheckedChange = onCheckedChange) },
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
                colors =
                    SettingsRowColors(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
            ),
        shape = shape,
    ) {
        StorageRowContent(cacheClearedMessage = cacheClearedMessage, clearCacheState = clearCacheState)
    }
}

// "About" already told the user their installed version - it's the natural home for whether
// that version is current, rather than a bare standalone "check for update" button floating on
// its own. "Automatic downloads" above governs catalog-app download behavior, a separate concern
@Composable
internal fun AboutRow(
    versionName: String,
    managerUpdateState: ManagerUpdateUiState,
    updateActions: ManagerUpdateActions,
    shape: Shape,
) {
    SettingsRow(
        header =
            SettingsRowHeader(
                icon = painterResource(R.drawable.ic_system_update_alt_rounded),
                title = "App Manager updates",
                subtitle = "Installed version $versionName",
                colors =
                    SettingsRowColors(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
            ),
        shape = shape,
    ) {
        ManagerUpdateSection(state = managerUpdateState, actions = updateActions)
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

private const val STATE_FADE_MS = 220

// every state gets the same icon-badge treatment used for Home's activity rows and every SettingsRow,
// instead of a bare tinted Icon floating in a Row. AnimatedContent smooths the Idle/Checking/Result
// swap instead of an abrupt layout jump each time the state changes
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ManagerUpdateSection(
    state: ManagerUpdateUiState,
    actions: ManagerUpdateActions,
) {
    AnimatedContent(
        targetState = state,
        label = "manager-update",
        transitionSpec = {
            fadeIn(tween(STATE_FADE_MS)) togetherWith fadeOut(tween(STATE_FADE_MS))
        },
        modifier = Modifier.fillMaxWidth(),
    ) { animatedState ->
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (animatedState) {
                is ManagerUpdateUiState.Idle -> {
                    ManagerUpdateStatusRow(
                        icon = painterResource(R.drawable.ic_system_update_alt_rounded),
                        badgeColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = "Check for a newer version of App Manager.",
                    )
                    FilledTonalButton(
                        onClick = actions.checkForUpdateState.onClick,
                        enabled = actions.checkForUpdateState.enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Check for updates")
                    }
                }

                is ManagerUpdateUiState.Checking -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .clip(ShapeCache.smooth12)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(modifier = Modifier.size(18.dp)) { LoadingIndicator() }
                        }
                        Text(text = "Checking for updates...", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                is ManagerUpdateUiState.Result ->
                    ManagerUpdateResultContent(status = animatedState.status, actions = actions)
            }
        }
    }
}

@Composable
private fun ManagerUpdateResultContent(
    status: ManagerUpdateStatus,
    actions: ManagerUpdateActions,
) {
    when (status) {
        is ManagerUpdateStatus.UpToDate -> {
            ManagerUpdateStatusRow(
                icon = painterResource(R.drawable.ic_check_circle_rounded),
                badgeColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                text = "You're on the latest version.",
            )
            CheckAgainButton(state = actions.checkForUpdateState)
        }

        is ManagerUpdateStatus.UpdateAvailable -> {
            ManagerUpdateStatusRow(
                icon = painterResource(R.drawable.ic_system_update_alt_rounded),
                badgeColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                text = "Version ${status.latestVersion} is available.",
                emphasize = true,
            )
            SelfUpdateAction(
                status = status,
                selfUpdateState = actions.selfUpdateState,
                onInstallUpdate = actions.onInstallUpdate,
            )
        }

        is ManagerUpdateStatus.NoReleasePublished -> {
            ManagerUpdateStatusRow(
                icon = painterResource(R.drawable.ic_info_rounded),
                badgeColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = "No manager releases have been published yet.",
            )
            CheckAgainButton(state = actions.checkForUpdateState)
        }

        is ManagerUpdateStatus.Failed -> {
            ManagerUpdateStatusRow(
                icon = painterResource(R.drawable.ic_error_rounded),
                badgeColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                text = status.reason,
            )
            CheckAgainButton(state = actions.checkForUpdateState, label = "Retry")
        }
    }
}

// the same debounced retry action under three different labels - only the wording differs, so this
// is the one place the button/enabled/fillMaxWidth wiring for it needs to be written out
@Composable
private fun CheckAgainButton(
    state: DebouncedButtonState,
    label: String = "Check again",
) {
    FilledTonalButton(onClick = state.onClick, enabled = state.enabled, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}

// internal, not private - also called from ManagerSelfUpdateContent.kt (same package)
@Composable
internal fun ManagerUpdateStatusRow(
    icon: Painter,
    badgeColor: Color,
    contentColor: Color,
    text: String,
    emphasize: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(36.dp).clip(ShapeCache.smooth12).background(badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        }
        Text(
            text = text,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
