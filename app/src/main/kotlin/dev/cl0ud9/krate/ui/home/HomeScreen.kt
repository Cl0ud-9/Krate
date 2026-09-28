package dev.cl0ud9.krate.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.ActivityAction
import dev.cl0ud9.krate.domain.model.ActivityEntry
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.platform.selfupdate.KrateUpdateStatus
import dev.cl0ud9.krate.platform.selfupdate.SelfUpdateState
import dev.cl0ud9.krate.ui.components.AnnouncementCard
import dev.cl0ud9.krate.ui.components.AppIconAvatar
import dev.cl0ud9.krate.ui.components.KrateMessage
import dev.cl0ud9.krate.ui.components.KratePullToRefreshBox
import dev.cl0ud9.krate.ui.components.KrateUpdateAnnouncementDialog
import dev.cl0ud9.krate.ui.components.LocalIntroPlaying
import dev.cl0ud9.krate.ui.components.LocalNavBarClearance
import dev.cl0ud9.krate.ui.components.RefreshFailureSnackbar
import dev.cl0ud9.krate.ui.components.WhatsNewDialog
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.RefreshOnResume
import dev.cl0ud9.krate.ui.util.formatRelativeTime
import dev.cl0ud9.krate.ui.util.krateViewModel
import dev.cl0ud9.krate.ui.util.rememberLastNonNull
import dev.cl0ud9.krate.voice.KrateVoice
import dev.cl0ud9.krate.voice.Moment
import dev.cl0ud9.krate.voice.rememberKrateLeadIn

private const val MAX_ACTIVITY_ROWS = 5
private val HOME_SECTION_GAP = 20.dp

// overall status, updates available, recent activity - section 30 of the spec. led by a status
// hero rather than a flat stat row, since "am I up to date, and what should I do about it" is the
// one thing this screen exists to answer - two disconnected numbers made the user do that math
// themselves, section 30 of the spec
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToApps: () -> Unit,
    onNavigateToUpdates: () -> Unit,
    onNavigateToApp: (String) -> Unit,
) {
    val viewModel =
        krateViewModel { container ->
            HomeViewModel(
                container.catalogRepository,
                container.installedPackageReader,
                container.activityLogRepository,
                container.krateUpdateChecker,
                container.githubCredentialStore,
                container.krateBaselineStore,
                container.announcementDismissalStore,
                container.krateSelfUpdateInstaller,
                container.whatsNewTracker,
            )
        }
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val updateAnnouncement by viewModel.updateAnnouncement.collectAsStateWithLifecycle()
    val selfUpdateState by viewModel.selfUpdateState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)

    // held back while the post-setup intro plays, then shown the moment the Krate mark lands
    val whatsNew by viewModel.whatsNew.collectAsStateWithLifecycle()
    if (!LocalIntroPlaying.current) {
        KrateUpdatePrompt(updateAnnouncement, selfUpdateState, viewModel)
        whatsNew?.takeIf { updateAnnouncement == null }?.let { release ->
            WhatsNewDialog(release = release, onDismiss = viewModel::dismissWhatsNew)
        }
    }

    // the counts on this screen are derived from the same catalog data Apps/Updates show, so a stale
    // manifest shows up here first - refreshFromNetwork() shares its result with every other screen
    // via the catalog repository's cache, so this pull is never wasted even if the user never leaves Home
    Box(modifier = Modifier.fillMaxSize()) {
        KratePullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refreshFromNetwork,
            modifier = Modifier.fillMaxSize(),
        ) {
            HomeContent(viewModel, onNavigateToApps, onNavigateToUpdates, onNavigateToApp)
        }
        RefreshFailureSnackbar(
            refreshFailed = viewModel.refreshFailed,
            message = {
                KrateMessage(
                    headline = KrateVoice.line(Moment.REFRESH_FAILED),
                    detail = "Couldn't refresh, so this is what Krate saw last.",
                    icon = R.drawable.ic_cloud_off_rounded,
                    actionLabel = "Retry",
                )
            },
            onRetry = viewModel::refreshFromNetwork,
        )
    }
}

@Composable
private fun HomeContent(
    viewModel: HomeViewModel,
    onNavigateToApps: () -> Unit,
    onNavigateToUpdates: () -> Unit,
    onNavigateToApp: (String) -> Unit,
) {
    val catalogCount by viewModel.catalogCount.collectAsStateWithLifecycle()
    val pendingUpdateCount by viewModel.pendingUpdateCount.collectAsStateWithLifecycle()
    val installedCount by viewModel.installedCount.collectAsStateWithLifecycle()
    val recentActivity by viewModel.recentActivity.collectAsStateWithLifecycle()
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val homeApps by viewModel.homeApps.collectAsStateWithLifecycle()
    val checking by viewModel.isRefreshing.collectAsStateWithLifecycle()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // the last section scrolls clear of the nav bar floating over the bottom
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + LocalNavBarClearance.current),
        verticalArrangement = Arrangement.spacedBy(HOME_SECTION_GAP),
    ) {
        // above the status hero - these only exist when something needs the user's attention
        announcements.forEach { item ->
            AnnouncementCard(item = item, onOpenApp = onNavigateToApp, onDismiss = viewModel::dismissAnnouncement)
        }

        HomeAppear(order = 0) {
            StatusHeroCard(
                pendingUpdateCount = pendingUpdateCount,
                checking = checking,
                onViewUpdates = onNavigateToUpdates,
                onCheckAgain = viewModel::refreshFromNetwork,
            )
        }

        StatsAndYourApps(
            counts = Triple(catalogCount, installedCount, pendingUpdateCount),
            installedApps = homeApps.filter { it.installed },
            onNavigateToApps = onNavigateToApps,
            onNavigateToUpdates = onNavigateToUpdates,
            onNavigateToApp = onNavigateToApp,
        )

        HomeAppear(order = 3) {
            RecentActivitySection(
                entries = recentActivity.take(MAX_ACTIVITY_ROWS),
                appsById = homeApps.associate { it.app.id to it.app },
                onOpenApp = onNavigateToApp,
            )
        }
    }
}

@Composable
private fun KrateUpdatePrompt(
    announcement: KrateUpdateStatus.UpdateAvailable?,
    selfUpdateState: SelfUpdateState?,
    viewModel: HomeViewModel,
) {
    if (announcement == null) return
    KrateUpdateAnnouncementDialog(
        status = announcement,
        selfUpdateState = selfUpdateState,
        onUpdate = viewModel::installKrateUpdate,
        onDismiss = viewModel::dismissUpdateAnnouncement,
    )
}

@Composable
private fun RecentActivitySection(
    entries: List<ActivityEntry>,
    appsById: Map<String, AppProfile>,
    onOpenApp: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "Recent activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeCache.rounded16,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            if (entries.isEmpty()) {
                Text(
                    text =
                        rememberKrateLeadIn(
                            Moment.NOTHING_UNPACKED,
                            "Installs and updates you run will show up here.",
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                Column {
                    entries.forEachIndexed { index, entry ->
                        ActivityRow(entry = entry, app = appsById[entry.appId], onOpenApp = onOpenApp)
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

// the app's own icon with a small badge for what happened; tapping the row opens that app
@Composable
private fun ActivityRow(
    entry: ActivityEntry,
    app: AppProfile?,
    onOpenApp: (String) -> Unit,
) {
    val presentation = activityPresentation(entry.action)
    val rowModifier =
        if (app !=
            null
        ) {
            Modifier.homeTappable(ShapeCache.rounded16) { onOpenApp(entry.appId) }
        } else {
            Modifier
        }
    Row(
        modifier = Modifier.fillMaxWidth().then(rowModifier).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(modifier = Modifier.size(44.dp)) {
            if (app != null) {
                AppIconAvatar(app = app, size = 44.dp)
            } else {
                Box(modifier = Modifier.fillMaxSize().clip(ShapeCache.rounded12).background(presentation.badgeColor))
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                        .padding(2.dp)
                        .background(presentation.badgeColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    presentation.icon,
                    contentDescription = null,
                    tint = presentation.onBadgeColor,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.appName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = activitySummary(entry, presentation.label, canRetry = app != null),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.onSurfaceVariant,
                "Uninstalled",
            )

        ActivityAction.FAILED ->
            ActivityPresentation(
                painterResource(R.drawable.ic_error_rounded),
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                "Failed",
            )
    }

// one block, so the gap above Your apps folds with it instead of snapping when the row comes or goes
@Composable
private fun StatsAndYourApps(
    counts: Triple<Int, Int, Int>,
    installedApps: List<HomeApp>,
    onNavigateToApps: () -> Unit,
    onNavigateToUpdates: () -> Unit,
    onNavigateToApp: (String) -> Unit,
) {
    val shownApps = rememberLastNonNull(installedApps.takeIf { it.isNotEmpty() })
    Column {
        HomeAppear(order = 1) {
            StatTiles(counts = counts, onOpenApps = onNavigateToApps, onOpenUpdates = onNavigateToUpdates)
        }
        AnimatedVisibility(
            visible = installedApps.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Box(modifier = Modifier.padding(top = HOME_SECTION_GAP)) {
                HomeAppear(order = 2) {
                    YourAppsRow(apps = shownApps.orEmpty(), onOpenApp = onNavigateToApp, onSeeAll = onNavigateToApps)
                }
            }
        }
    }
}
