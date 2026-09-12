package dev.cl0ud9.manager.ui.details

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.DownloadStatus
import dev.cl0ud9.manager.domain.model.InstallStatus
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.ui.components.AppIconAvatar
import dev.cl0ud9.manager.ui.components.SectionHeader
import dev.cl0ud9.manager.ui.components.SupportStatusBadge
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.util.RefreshOnResume
import dev.cl0ud9.manager.ui.util.formatMarkdownLite
import dev.cl0ud9.manager.ui.util.managerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppDetailsScreen(
    appId: String,
    onNavigateToApp: (String) -> Unit,
) {
    val viewModel =
        managerViewModel { container ->
            AppDetailsViewModel(
                container.catalogRepository,
                container.artifactDownloader,
                container.installationEngine,
                container.cleanInstallOrchestrator,
                container.installedPackageReader,
                container.activityLogRepository,
                appId,
            )
        }
    RefreshOnResume(viewModel::refresh)
    val app by viewModel.app.collectAsStateWithLifecycle()
    val installedVersionName by viewModel.installedVersionName.collectAsStateWithLifecycle()
    val dependencies by viewModel.dependencies.collectAsStateWithLifecycle()
    val downloadStatus by viewModel.downloadStatus.collectAsStateWithLifecycle()
    val installStatus by viewModel.installStatus.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    val currentApp = app
    // a single-app manifest entry can also change server-side (new version, updated release notes),
    // so this screen shares the same pull-to-refresh path as the catalog list screens
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refreshFromNetwork,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (currentApp == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        } else {
            AppDetailsContent(
                state =
                    AppDetailsUiState(
                        app = currentApp,
                        installedVersionName = installedVersionName,
                        dependencies = dependencies,
                        downloadStatus = downloadStatus,
                        installStatus = installStatus,
                    ),
                onDownload = viewModel::startDownload,
                onInstall = viewModel::startInstall,
                onRetryAsCleanInstall = viewModel::retryAsCleanInstall,
                onNavigateToApp = onNavigateToApp,
            )
        }
    }
}

// bundles the screen's state so the composables below stay under the parameter-count limit
internal data class AppDetailsUiState(
    val app: AppProfile,
    val installedVersionName: String?,
    val dependencies: List<DependencyInfo>,
    val downloadStatus: DownloadStatus,
    val installStatus: InstallStatus,
)

@Composable
private fun AppDetailsContent(
    state: AppDetailsUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetryAsCleanInstall: () -> Unit,
    onNavigateToApp: (String) -> Unit,
) {
    val app = state.app
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // one compact header block instead of three stacked rows (name/package, badge/version,
        // installed status) - each of those is a short fragment on its own and reads as more
        // intentional grouped together than as separate full-width rows with their own gaps
        AppDetailsHeader(app = app, installedVersionName = state.installedVersionName)

        // the primary action moves right under the header instead of sitting below Release notes,
        // which could push it off-screen for apps with long release notes - a detail page exists
        // to get the user to this action, so it should not be the thing they have to scroll to find
        DownloadSection(
            state = state,
            onDownload = onDownload,
            onInstall = onInstall,
            onRetryAsCleanInstall = onRetryAsCleanInstall,
        )

        // Installation and Dependencies are both short, glanceable facts - side by side when
        // Dependencies has nothing to list (the common case) makes better use of the available width;
        // an app with actual pending dependencies needs the full row width for readable name/status/chevron,
        // so that case stays stacked instead of cramming into half the screen
        if (state.dependencies.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InstallationSection(app = app, modifier = Modifier.weight(1f))
                DependenciesSection(
                    dependencies = state.dependencies,
                    onNavigateToApp = onNavigateToApp,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            InstallationSection(app = app)
            DependenciesSection(dependencies = state.dependencies, onNavigateToApp = onNavigateToApp)
        }

        ReleaseNotesSection(app = app)
    }
}

@Composable
private fun AppDetailsHeader(
    app: AppProfile,
    installedVersionName: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AppIconAvatar(displayName = app.displayName, seed = app.id, size = 56.dp, packageName = app.packageName)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = app.displayName, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SupportStatusBadge(status = app.supportStatus)
                Text(
                    text = app.latestVersionName?.let { "Latest $it" } ?: "Latest version unknown",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            InstalledStatusRow(installedVersionName = installedVersionName)
        }
    }
}

@Composable
private fun InstalledStatusRow(installedVersionName: String?) {
    val text = installedVersionName?.let { "Installed - version $it" } ?: "Not installed on this device"
    val tint =
        if (installedVersionName != null) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    val icon = if (installedVersionName != null) Icons.Filled.CheckCircle else null
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}

@Composable
private fun InstallationSection(
    app: AppProfile,
    modifier: Modifier = Modifier,
) {
    DetailSection(
        title = "Installation",
        icon = Icons.Filled.Build,
        modifier = modifier,
        body =
            AnnotatedString(
                when (app.installationMode) {
                    InstallationMode.UPDATE -> {
                        "Updates are attempted in place. If that fails, a clean install is offered."
                    }

                    InstallationMode.CLEAN_INSTALL -> {
                        "This app always uses a clean install: uninstall then install the new version."
                    }
                },
            ),
    )
}

// long release notes used to push the primary action further down the page and add a lot of scroll
// distance for something most users only skim - collapsed to a few lines with an explicit expand
// affordance keeps the information available without it dominating the page by default
@Composable
private fun ReleaseNotesSection(app: AppProfile) {
    var expanded by remember(app.id) { mutableStateOf(false) }
    val body = (app.releaseNotes ?: "No release notes available.").formatMarkdownLite()

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(
                    title = "Release notes",
                    icon = Icons.Filled.Description,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Show less" else "Show more",
                    )
                }
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_RELEASE_NOTES_LINES,
            )
        }
    }
}

private const val COLLAPSED_RELEASE_NOTES_LINES = 4

// only ever used with the default badge colors now that Release notes has its own tertiary-tinted
// Card above - keeping it to title/icon/body/modifier avoids an unused customization surface
@Composable
private fun DetailSection(
    title: String,
    icon: ImageVector,
    body: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(title = title, icon = icon)
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
