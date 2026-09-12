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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
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
import dev.cl0ud9.manager.ui.components.SectionHeader
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.util.managerViewModel

// update-check prefs, notifications, storage, diagnostics - section 30 of the spec
// notification prefs, storage management and diagnostics land alongside the phases that need them
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

    // grouped into cards rather than bare dividers, matching the card language every other screen uses
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsSection(title = "Updates", icon = Icons.Filled.Update) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Automatic downloads", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Download updates in the background. Installing always needs your confirmation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = automaticDownloads, onCheckedChange = viewModel::setAutomaticDownloads)
            }
        }

        SettingsSection(
            title = "Storage",
            icon = Icons.Filled.DeleteSweep,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            StorageSectionContent(cacheClearedMessage = cacheClearedMessage, onClearCache = viewModel::clearCache)
        }

        // "About" already told the user their installed version - it's the natural home for whether
        // that version is current, rather than a bare standalone "check for update" button floating
        // on its own. "Updates" above governs catalog-app download behavior, a separate concern
        SettingsSection(
            title = "About",
            icon = Icons.Filled.Info,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            AppVersionRow()
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "Manager updates",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ManagerUpdateSection(state = managerUpdateState, onCheck = viewModel::checkForManagerUpdate)
        }
    }
}

private const val STATE_FADE_MS = 220

// every state gets the same icon-badge treatment used for Home's activity rows and every card's
// SectionHeader, instead of a bare tinted Icon floating in a Row - that inconsistency (plain icon
// here, badged everywhere else) was the concrete thing making this block read as bolted-on rather
// than a designed part of the screen. AnimatedContent smooths the Idle/Checking/Result swap instead
// of an abrupt layout jump each time the state changes
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

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(title = title, icon = icon, containerColor = containerColor, contentColor = contentColor)
            content()
        }
    }
}

@Composable
private fun StorageSectionContent(
    cacheClearedMessage: String?,
    onClearCache: () -> Unit,
) {
    Text(
        text =
            "Downloaded apks are removed right after a successful install. This clears " +
                "anything left over from an interrupted or abandoned download.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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

@Composable
private fun AppVersionRow() {
    val context = LocalContext.current
    val versionName =
        remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                .getOrNull() ?: "unknown"
        }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = "Version", style = MaterialTheme.typography.bodyLarge)
        Text(text = versionName, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
