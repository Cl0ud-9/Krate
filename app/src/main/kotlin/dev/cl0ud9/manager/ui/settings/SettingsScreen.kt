package dev.cl0ud9.manager.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.manager.platform.selfupdate.ManagerUpdateStatus
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.util.managerViewModel

// a flowing list of icon-badged category rows (badge + title + subtitle, expanding into the row's
// own controls) instead of plain text blocks inside flat cards, so settings feel
// considered rather than default-Material-boilerplate. our settings surface is small (three groupings),
// so this keeps that honest scale rather than inventing categories we don't have
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen() {
    val viewModel =
        managerViewModel { container ->
            SettingsViewModel(
                container.settingsRepository,
                container.artifactDownloader,
                container.managerUpdateChecker,
            )
        }
    val automaticDownloads by viewModel.automaticDownloads.collectAsStateWithLifecycle()
    val cacheClearedMessage by viewModel.cacheClearedMessage.collectAsStateWithLifecycle()
    val managerUpdateState by viewModel.managerUpdateState.collectAsStateWithLifecycle()
    val versionName = rememberVersionName()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AutomaticDownloadsRow(checked = automaticDownloads, onCheckedChange = viewModel::setAutomaticDownloads)
        StorageRow(cacheClearedMessage = cacheClearedMessage, onClearCache = viewModel::clearCache)
        AboutRow(
            versionName = versionName,
            managerUpdateState = managerUpdateState,
            onCheck = viewModel::checkForManagerUpdate,
        )
    }
}

@Composable
private fun AutomaticDownloadsRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRow(
        header =
            SettingsRowHeader(
                icon = Icons.Filled.Update,
                title = "Automatic downloads",
                subtitle = "Download updates in the background. Installing always needs your confirmation.",
                colors = defaultSettingsRowColors(),
            ),
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
private fun StorageRow(
    cacheClearedMessage: String?,
    onClearCache: () -> Unit,
) {
    SettingsRow(
        header =
            SettingsRowHeader(
                icon = Icons.Filled.DeleteSweep,
                title = "Storage",
                subtitle = "Downloaded apks are removed right after a successful install.",
                colors =
                    SettingsRowColors(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
            ),
    ) {
        StorageRowContent(cacheClearedMessage = cacheClearedMessage, onClearCache = onClearCache)
    }
}

// "About" already told the user their installed version - it's the natural home for whether
// that version is current, rather than a bare standalone "check for update" button floating on
// its own. "Automatic downloads" above governs catalog-app download behavior, a separate concern
@Composable
private fun AboutRow(
    versionName: String,
    managerUpdateState: ManagerUpdateUiState,
    onCheck: () -> Unit,
) {
    SettingsRow(
        header =
            SettingsRowHeader(
                icon = Icons.Filled.Info,
                title = "About",
                subtitle = "Version $versionName",
                colors =
                    SettingsRowColors(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
            ),
    ) {
        Text(
            text = "Manager updates",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ManagerUpdateSection(state = managerUpdateState, onCheck = onCheck)
    }
}

@Composable
private fun rememberVersionName(): String {
    val context = LocalContext.current
    return remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "unknown"
    }
}

@Composable
private fun StorageRowContent(
    cacheClearedMessage: String?,
    onClearCache: () -> Unit,
) {
    OutlinedButton(onClick = onClearCache, modifier = Modifier.fillMaxWidth()) {
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
    onCheck: () -> Unit,
) {
    AnimatedContent(
        targetState = state,
        label = "manager-update",
        transitionSpec = {
            fadeIn(tween(STATE_FADE_MS)) togetherWith fadeOut(tween(STATE_FADE_MS))
        },
    ) { animatedState ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (animatedState) {
                is ManagerUpdateUiState.Idle -> {
                    ManagerUpdateStatusRow(
                        icon = Icons.Filled.SystemUpdateAlt,
                        badgeColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = "Check GitHub for a newer release of the manager itself.",
                    )
                    OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
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
                    ManagerUpdateResultContent(
                        status = animatedState.status,
                        onCheck = onCheck,
                    )
            }
        }
    }
}

@Composable
private fun ManagerUpdateResultContent(
    status: ManagerUpdateStatus,
    onCheck: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    when (status) {
        is ManagerUpdateStatus.UpToDate -> {
            ManagerUpdateStatusRow(
                icon = Icons.Filled.CheckCircle,
                badgeColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                text = "You're on the latest version.",
            )
            OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                Text("Check again")
            }
        }

        is ManagerUpdateStatus.UpdateAvailable -> {
            ManagerUpdateStatusRow(
                icon = Icons.Filled.SystemUpdateAlt,
                badgeColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                text = "Version ${status.latestVersion} is available.",
                emphasize = true,
            )
            Button(onClick = { uriHandler.openUri(status.releaseUrl) }, modifier = Modifier.fillMaxWidth()) {
                Text("View release on GitHub")
            }
        }

        is ManagerUpdateStatus.NoReleasePublished -> {
            ManagerUpdateStatusRow(
                icon = Icons.Filled.Info,
                badgeColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = "No manager releases have been published yet.",
            )
            OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                Text("Check again")
            }
        }

        is ManagerUpdateStatus.Failed -> {
            ManagerUpdateStatusRow(
                icon = Icons.Filled.Error,
                badgeColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                text = status.reason,
            )
            OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun ManagerUpdateStatusRow(
    icon: ImageVector,
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
