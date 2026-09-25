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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
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
import dev.cl0ud9.manager.ui.navigation.DetailContentTopGap
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.util.DebouncedButtonState
import dev.cl0ud9.manager.ui.util.rememberDebouncedButtonState

// a flowing list of icon-badged category rows (badge + title + subtitle, expanding into the row's
// own controls) instead of plain text blocks inside flat cards, so settings feel
// considered rather than default-Material-boilerplate. our settings surface is small (three groupings),
// so this keeps that honest scale rather than inventing categories we don't have
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    scrollState: ScrollState,
    topContentPadding: Dp,
    onNavigateToAppearance: () -> Unit,
) {
    val viewModel = rememberSettingsViewModel()
    val automaticDownloads by viewModel.automaticDownloads.collectAsStateWithLifecycle()
    val cacheClearedMessage by viewModel.cacheClearedMessage.collectAsStateWithLifecycle()
    val managerUpdateState by viewModel.managerUpdateState.collectAsStateWithLifecycle()
    val hasGitHubToken by viewModel.hasGitHubToken.collectAsStateWithLifecycle()
    val feedbackState = rememberFeedbackUiState(viewModel)
    val deviceSummary = rememberDeviceSummary()
    // both actions are already idempotent in the ViewModel itself (a second call while one is
    // still running is a no-op) - this debounce is the UI-side half of that: the button itself goes
    // disabled for the cooldown, so a fast repeat tap can't stack a second ripple on top of the
    // first one still playing, on top of never reaching the ViewModel a second time either
    val clearCacheState = rememberDebouncedButtonState(onClick = viewModel::clearCache)
    // while a manager update is waiting, About (with its Update now button) moves to the top of the
    // list, so opening Settings from the update notification lands right on it
    val updateWaiting =
        (managerUpdateState as? ManagerUpdateUiState.Result)?.status is ManagerUpdateStatus.UpdateAvailable
    val offset = if (updateWaiting) 1 else 0

    // one continuous grouped list (2dp seams, square-ish touching corners) instead of four
    // separately-floating cards - settingsGroupShape needs each row's position in the group.
    // scrollState/topContentPadding come from the shared collapsing header this screen is hosted
    // in - without that top space, the heading would sit on top of the Appearance row instead of
    // sliding away above it
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = topContentPadding + DetailContentTopGap, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (updateWaiting) SettingsAboutRow(viewModel, settingsGroupShape(0, SETTINGS_ROW_COUNT))
        AppearanceRow(
            onClick = onNavigateToAppearance,
            shape = settingsGroupShape(APPEARANCE_ROW_INDEX + offset, SETTINGS_ROW_COUNT),
        )
        AutomaticDownloadsRow(
            checked = automaticDownloads,
            onCheckedChange = viewModel::setAutomaticDownloads,
            shape = settingsGroupShape(AUTOMATIC_DOWNLOADS_ROW_INDEX + offset, SETTINGS_ROW_COUNT),
        )
        StorageRow(
            cacheClearedMessage = cacheClearedMessage,
            clearCacheState = clearCacheState,
            shape = settingsGroupShape(STORAGE_ROW_INDEX + offset, SETTINGS_ROW_COUNT),
        )
        GitHubAccessRow(
            hasToken = hasGitHubToken,
            onSaveToken = viewModel::setGitHubToken,
            onClearToken = viewModel::clearGitHubToken,
            shape = settingsGroupShape(GITHUB_ACCESS_ROW_INDEX + offset, SETTINGS_ROW_COUNT),
        )
        FeedbackRow(
            state = feedbackState,
            onFeedbackTextChange = viewModel::setFeedbackText,
            onGenerateReport = { viewModel.generateDiagnosticReport(deviceSummary) },
            shape = settingsGroupShape(FEEDBACK_ROW_INDEX + offset, SETTINGS_ROW_COUNT),
        )
        if (!updateWaiting) SettingsAboutRow(viewModel, settingsGroupShape(ABOUT_ROW_INDEX, SETTINGS_ROW_COUNT))
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private const val APPEARANCE_ROW_INDEX = 0
private const val AUTOMATIC_DOWNLOADS_ROW_INDEX = 1
private const val STORAGE_ROW_INDEX = 2
private const val GITHUB_ACCESS_ROW_INDEX = 3
private const val FEEDBACK_ROW_INDEX = 4
private const val ABOUT_ROW_INDEX = 5
private const val SETTINGS_ROW_COUNT = 6

@Composable
private fun AutomaticDownloadsRow(
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
private fun StorageRow(
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
                icon = painterResource(R.drawable.ic_info_rounded),
                title = "About",
                subtitle = "Version $versionName",
                colors =
                    SettingsRowColors(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
            ),
        shape = shape,
    ) {
        Text(
            text = "Manager updates",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
