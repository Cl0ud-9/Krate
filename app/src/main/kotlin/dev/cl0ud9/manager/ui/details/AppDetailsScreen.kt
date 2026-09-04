package dev.cl0ud9.manager.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.DownloadStatus
import dev.cl0ud9.manager.domain.model.InstallStatus
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.ui.components.AppIconAvatar
import dev.cl0ud9.manager.ui.components.SupportStatusBadge
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.util.RefreshOnResume
import dev.cl0ud9.manager.ui.util.managerViewModel

@Composable
fun AppDetailsScreen(
    appId: String,
    onBack: () -> Unit,
) {
    val viewModel =
        managerViewModel { container ->
            AppDetailsViewModel(
                container.catalogRepository,
                container.artifactDownloader,
                container.installationEngine,
                container.cleanInstallOrchestrator,
                container.installedPackageReader,
                appId,
            )
        }
    RefreshOnResume(viewModel::refresh)
    val app by viewModel.app.collectAsStateWithLifecycle()
    val installedVersionName by viewModel.installedVersionName.collectAsStateWithLifecycle()
    val downloadStatus by viewModel.downloadStatus.collectAsStateWithLifecycle()
    val installStatus by viewModel.installStatus.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("App Details", style = MaterialTheme.typography.titleLarge)
        }

        val currentApp = app
        if (currentApp == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            AppDetailsContent(
                state =
                    AppDetailsUiState(
                        app = currentApp,
                        installedVersionName = installedVersionName,
                        downloadStatus = downloadStatus,
                        installStatus = installStatus,
                    ),
                onDownload = viewModel::startDownload,
                onInstall = viewModel::startInstall,
                onRetryAsCleanInstall = viewModel::retryAsCleanInstall,
            )
        }
    }
}

// bundles the screen's state so the composables below stay under the parameter-count limit
internal data class AppDetailsUiState(
    val app: AppProfile,
    val installedVersionName: String?,
    val downloadStatus: DownloadStatus,
    val installStatus: InstallStatus,
)

@Composable
private fun AppDetailsContent(
    state: AppDetailsUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetryAsCleanInstall: () -> Unit,
) {
    val app = state.app
    val installedVersionName = state.installedVersionName
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppIconAvatar(displayName = app.displayName, seed = app.id, size = 56.dp)
            Column {
                Text(text = app.displayName, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SupportStatusBadge(status = app.supportStatus)
            Text(
                text = app.latestVersionName?.let { "Latest $it" } ?: "Latest version unknown",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        InstalledStatusRow(installedVersionName = installedVersionName)

        DetailSection(
            title = "Installation",
            body =
                when (app.installationMode) {
                    InstallationMode.UPDATE -> {
                        "Updates are attempted in place. If that fails, a clean install is offered."
                    }

                    InstallationMode.CLEAN_INSTALL -> {
                        "This app always uses a clean install: uninstall then install the new version."
                    }
                },
        )

        DetailSection(
            title = "Dependencies",
            body = if (app.dependencyIds.isEmpty()) "No dependencies." else app.dependencyIds.joinToString(", "),
        )

        DetailSection(
            title = "Release notes",
            body = app.releaseNotes ?: "No release notes available.",
        )

        DownloadSection(
            state = state,
            onDownload = onDownload,
            onInstall = onInstall,
            onRetryAsCleanInstall = onRetryAsCleanInstall,
        )
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
private fun DetailSection(
    title: String,
    body: String,
) {
    Card(
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
