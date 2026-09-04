package dev.cl0ud9.manager.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
            )
        }
    }
}

// bundles the screen's state so the composables below stay under the parameter-count limit
private data class AppDetailsUiState(
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
        )
    }
}

@Composable
private fun InstalledStatusRow(installedVersionName: String?) {
    val text = installedVersionName?.let { "Installed - version $it" } ?: "Not installed on this device"
    val tint =
        if (installedVersionName !=
            null
        ) {
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

// section 16 of the spec: the ui shows Install or Update based on real device state, not just app metadata
private fun actionLabelFor(
    app: AppProfile,
    installedVersionName: String?,
): String =
    when (app.installationMode) {
        InstallationMode.CLEAN_INSTALL -> "Install"
        InstallationMode.UPDATE -> if (installedVersionName != null) "Update" else "Install"
    }

@Composable
private fun DownloadSection(
    state: AppDetailsUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val app = state.app
    val status = state.downloadStatus
    val installStatus = state.installStatus
    val actionLabel = actionLabelFor(app, state.installedVersionName)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (status) {
            is DownloadStatus.Idle -> {
                Button(onClick = onDownload, enabled = app.artifact != null, modifier = Modifier.fillMaxWidth()) {
                    Text("Download")
                }
                if (app.artifact == null) {
                    HelperText("Not yet available for download.")
                }
            }

            is DownloadStatus.Downloading -> {
                val total = status.totalBytes
                val fraction = if (total != null && total > 0) status.bytesDownloaded / total.toFloat() else 0f
                if (total != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(6.dp))
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp))
                }
                HelperText(
                    "Downloading ${formatMb(status.bytesDownloaded)} of ${total?.let { formatMb(it) } ?: "?"} MB",
                )
            }

            is DownloadStatus.Verifying -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp))
                HelperText("Verifying checksum and signing certificate...")
            }

            is DownloadStatus.ReadyToInstall -> {
                ReadyToInstallSection(
                    app = app,
                    actionLabel = actionLabel,
                    installStatus = installStatus,
                    onInstall = onInstall,
                )
            }

            is DownloadStatus.Failed -> {
                StatusRow(icon = Icons.Filled.Error, tint = MaterialTheme.colorScheme.error, text = status.reason)
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Text("Retry download")
                }
            }
        }
    }
}

@Composable
private fun ReadyToInstallSection(
    app: AppProfile,
    actionLabel: String,
    installStatus: InstallStatus,
    onInstall: () -> Unit,
) {
    // clean install (uninstall + install, e.g. videoapp patched) is phase 5, not wired up yet
    val canInstall = app.installationMode == InstallationMode.UPDATE
    when (installStatus) {
        is InstallStatus.Idle,
        is InstallStatus.Failed,
        -> {
            if (installStatus is InstallStatus.Failed) {
                StatusRow(
                    icon = Icons.Filled.Error,
                    tint = MaterialTheme.colorScheme.error,
                    text = installStatus.reason,
                )
            }
            Button(onClick = onInstall, enabled = canInstall, modifier = Modifier.fillMaxWidth()) {
                Text(actionLabel)
            }
            if (!canInstall) {
                HelperText("Verified. $actionLabel lands in a later phase for this app.")
            }
        }

        is InstallStatus.Installing -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp))
            HelperText("Installing...")
        }

        is InstallStatus.WaitingForUser -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp))
            HelperText("Confirm the installation in the system dialog.")
        }

        is InstallStatus.Success -> {
            StatusRow(
                icon = Icons.Filled.CheckCircle,
                tint = MaterialTheme.colorScheme.primary,
                text = "$actionLabel complete.",
            )
        }
    }
}

@Composable
private fun StatusRow(
    icon: ImageVector,
    tint: Color,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HelperText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val BYTES_PER_MB = 1024 * 1024

private fun formatMb(bytes: Long): String = "%.1f".format(bytes / BYTES_PER_MB.toFloat())

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
