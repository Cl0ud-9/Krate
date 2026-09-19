package dev.cl0ud9.manager.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.domain.model.ActivityAction
import dev.cl0ud9.manager.domain.model.ActivityEntry
import dev.cl0ud9.manager.platform.selfupdate.ManagerUpdateStatus
import dev.cl0ud9.manager.ui.components.ManagerPullToRefreshBox
import dev.cl0ud9.manager.ui.components.ManagerUpdateAnnouncementDialog
import dev.cl0ud9.manager.ui.components.SectionHeader
import dev.cl0ud9.manager.ui.components.StatTile
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.util.RefreshOnResume
import dev.cl0ud9.manager.ui.util.formatRelativeTime
import dev.cl0ud9.manager.ui.util.managerViewModel

private const val MAX_ACTIVITY_ROWS = 5

// overall status, updates available, recent activity - section 30 of the spec. led by a status
// hero rather than a flat stat row, since "am I up to date, and what should I do about it" is the
// one thing this screen exists to answer - two disconnected numbers made the user do that math
// themselves, section 30 of the spec
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToApps: () -> Unit,
    onNavigateToUpdates: () -> Unit,
) {
    val viewModel =
        managerViewModel { container ->
            HomeViewModel(
                container.catalogRepository,
                container.installedPackageReader,
                container.activityLogRepository,
                container.managerUpdateChecker,
                container.githubCredentialStore,
            )
        }
    val catalogCount by viewModel.catalogCount.collectAsStateWithLifecycle()
    val pendingUpdateCount by viewModel.pendingUpdateCount.collectAsStateWithLifecycle()
    val installedCount by viewModel.installedCount.collectAsStateWithLifecycle()
    val recentActivity by viewModel.recentActivity.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val updateAnnouncement by viewModel.updateAnnouncement.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)

    HomeUpdateAnnouncement(announcement = updateAnnouncement, onDismiss = viewModel::dismissUpdateAnnouncement)

    // the counts on this screen are derived from the same catalog data Apps/Updates show, so a stale
    // manifest shows up here first - refreshFromNetwork() shares its result with every other screen
    // via the catalog repository's cache, so this pull is never wasted even if the user never leaves Home
    ManagerPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refreshFromNetwork,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            StatusHeroCard(pendingUpdateCount = pendingUpdateCount, onViewUpdates = onNavigateToUpdates)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = "Apps in catalog",
                    value = catalogCount.toString(),
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToApps,
                )
                StatTile(
                    label = "Installed on device",
                    value = installedCount.toString(),
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToApps,
                )
            }

            RecentActivitySection(entries = recentActivity.take(MAX_ACTIVITY_ROWS))
        }
    }
}

@Composable
private fun HomeUpdateAnnouncement(
    announcement: ManagerUpdateStatus.UpdateAvailable?,
    onDismiss: () -> Unit,
) {
    if (announcement == null) return
    val uriHandler = LocalUriHandler.current
    ManagerUpdateAnnouncementDialog(
        latestVersion = announcement.latestVersion,
        onDismiss = onDismiss,
        onViewRelease = {
            uriHandler.openUri(announcement.releaseUrl)
            onDismiss()
        },
    )
}

@Composable
private fun StatusHeroCard(
    pendingUpdateCount: Int,
    onViewUpdates: () -> Unit,
) {
    val upToDate = pendingUpdateCount == 0
    val containerColor =
        if (upToDate) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    val contentColor =
        if (upToDate) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.smooth28,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val heroIconRes =
                if (upToDate) R.drawable.ic_check_circle_rounded else R.drawable.ic_system_update_alt_rounded
            Icon(
                painter = painterResource(heroIconRes),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text =
                    when {
                        upToDate -> "You're all caught up"
                        pendingUpdateCount == 1 -> "1 update available"
                        else -> "$pendingUpdateCount updates available"
                    },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text =
                    if (upToDate) {
                        "Every installed app matches the catalog's latest version."
                    } else {
                        "Review and install the latest versions from the Updates tab."
                    },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!upToDate) {
                Button(
                    onClick = onViewUpdates,
                    colors = ButtonDefaults.buttonColors(containerColor = contentColor, contentColor = containerColor),
                ) {
                    Text("View updates")
                }
            }
        }
    }
}

@Composable
private fun RecentActivitySection(entries: List<ActivityEntry>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Recent activity",
            icon = rememberVectorPainter(Icons.Filled.History),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeCache.smooth16,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            if (entries.isEmpty()) {
                Text(
                    text = "Installs and updates you run will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                Column {
                    entries.forEachIndexed { index, entry ->
                        ActivityRow(entry)
                        if (index != entries.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 64.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(entry: ActivityEntry) {
    val presentation = activityPresentation(entry.action)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(ShapeCache.smooth12).background(presentation.badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                presentation.icon,
                contentDescription = null,
                tint = presentation.onBadgeColor,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.appName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = presentation.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatRelativeTime(entry.timestampMillis),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class ActivityPresentation(
    val icon: Painter,
    val badgeColor: Color,
    val onBadgeColor: Color,
    val label: String,
)

@Composable
private fun activityPresentation(action: ActivityAction): ActivityPresentation =
    when (action) {
        ActivityAction.INSTALLED ->
            ActivityPresentation(
                rememberVectorPainter(Icons.Filled.Download),
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                "Installed",
            )

        ActivityAction.UPDATED ->
            ActivityPresentation(
                painterResource(R.drawable.ic_system_update_alt_rounded),
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
                "Updated",
            )

        ActivityAction.UNINSTALLED ->
            ActivityPresentation(
                rememberVectorPainter(Icons.Filled.Delete),
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                "Uninstalled",
            )
    }
