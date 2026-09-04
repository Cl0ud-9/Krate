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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.DownloadStatus
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.ui.components.SupportStatusBadge
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.util.managerViewModel

@Composable
fun AppDetailsScreen(
    appId: String,
    onBack: () -> Unit,
) {
    val viewModel =
        managerViewModel { container ->
            AppDetailsViewModel(container.catalogRepository, container.artifactDownloader, appId)
        }
    val app by viewModel.app.collectAsStateWithLifecycle()
    val downloadStatus by viewModel.downloadStatus.collectAsStateWithLifecycle()

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
            AppDetailsContent(currentApp, downloadStatus, onDownload = viewModel::startDownload)
        }
    }
}

@Composable
private fun AppDetailsContent(
    app: AppProfile,
    downloadStatus: DownloadStatus,
    onDownload: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {}
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
                text = app.latestVersionName?.let { "Version $it" } ?: "Version unknown",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

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

        DownloadSection(app = app, status = downloadStatus, onDownload = onDownload)
    }
}

@Composable
private fun DownloadSection(
    app: AppProfile,
    status: DownloadStatus,
    onDownload: () -> Unit,
) {
    val actionLabel = if (app.installationMode == InstallationMode.CLEAN_INSTALL) "Install" else "Update"
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Verified and ready. $actionLabel lands in the next phase.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            is DownloadStatus.Failed -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(status.reason, style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Text("Retry download")
                }
            }
        }
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
