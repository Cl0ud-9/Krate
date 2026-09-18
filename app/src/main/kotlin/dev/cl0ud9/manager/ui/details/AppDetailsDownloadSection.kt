package dev.cl0ud9.manager.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.DownloadStatus
import dev.cl0ud9.manager.domain.model.InstallStatus
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.model.WaitingForUserStep
import dev.cl0ud9.manager.ui.components.SectionHeader
import dev.cl0ud9.manager.ui.theme.ShapeCache

// section 16 of the spec: the ui shows Install, Update, or Reinstall based on real device state
// compared against whichever version is currently selected (App Details' version history lets
// that be an older retained one, not always the latest), not just the installation mode - a bare
// "Update" whenever anything at all was installed (the old logic) is wrong once the installed
// version already matches the selected one: there is nothing to update to, so this now says
// "Reinstall" instead. Mode no longer drives the label at all: the Installation card below
// already explains the clean-install mechanics separately, so this only needs to answer "is there
// something new"
private fun actionLabelFor(
    selectedVersionName: String?,
    installedVersionName: String?,
): String =
    when {
        installedVersionName == null -> "Install"
        installedVersionName == selectedVersionName -> "Reinstall"
        else -> "Update"
    }

@Composable
internal fun DownloadSection(
    state: AppDetailsUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetryAsCleanInstall: () -> Unit,
) {
    val app = state.app
    val status = state.downloadStatus
    val actionLabel = actionLabelFor(state.selectedArtifact?.versionName, state.installedVersionName)
    // boxed in a card like every other detail section, instead of sitting bare on the screen background
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(title = "Get this app", icon = Icons.Filled.Download)
            when (status) {
                is DownloadStatus.Idle -> IdleContent(state = state, onDownload = onDownload)

                is DownloadStatus.Downloading -> {
                    val total = status.totalBytes
                    val fraction = if (total != null && total > 0) status.bytesDownloaded / total.toFloat() else 0f
                    ManagerLinearProgress(progress = if (total != null) fraction else null)
                    HelperText(
                        "Downloading ${formatMb(status.bytesDownloaded)} of ${total?.let { formatMb(it) } ?: "?"} MB",
                    )
                }

                is DownloadStatus.Verifying -> {
                    ManagerLinearProgress(progress = null)
                    HelperText("Verifying checksum and signing certificate...")
                }

                is DownloadStatus.ReadyToInstall -> {
                    ReadyToInstallSection(
                        state = state,
                        actionLabel = actionLabel,
                        onInstall = onInstall,
                        onRetryAsCleanInstall = onRetryAsCleanInstall,
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
}

// installedVersionName already matching the catalog's latest means there is nothing pending - a
// prominent "Download" button here would wrongly suggest otherwise. Redownloading (e.g. to repair a
// corrupted install) is still possible, just de-emphasized instead of being the primary action.
@Composable
private fun IdleContent(
    state: AppDetailsUiState,
    onDownload: () -> Unit,
) {
    val selected = state.selectedArtifact
    val upToDate = state.installedVersionName != null && state.installedVersionName == selected?.versionName
    if (upToDate) {
        // selected is necessarily non-null here: upToDate can only be true when its versionName
        // matched a real installedVersionName
        StatusRow(icon = Icons.Filled.CheckCircle, tint = MaterialTheme.colorScheme.tertiary, text = "Up to date.")
        OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
            Text("Redownload")
        }
    } else {
        Button(onClick = onDownload, enabled = selected != null, modifier = Modifier.fillMaxWidth()) {
            Text("Download")
        }
        if (selected == null) {
            HelperText("Not yet available for download.")
        }
    }
}

// every in-progress state below shares this exact indicator - one definition instead of six copies.
// determinate progress (real download bytes) keeps the wavy linear bar, since a filled fraction is
// genuinely informative there. indeterminate states (installing, uninstalling, waiting for the user)
// used to reuse the same indeterminate wavy bar, but that animates as two independently-phased wavy
// segments chasing each other - readable as an actual progress bar when it's genuinely determinate,
// but noisy and easy to misread as "two bars" when there is no real progress fraction behind it. the
// expressive LoadingIndicator (a single morphing shape) is Material's own component for exactly this
// indeterminate case, so it replaces the wavy bar rather than reusing it just because it's already wired up
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ManagerLinearProgress(progress: Float?) {
    if (progress != null) {
        LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    } else {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
    }
}

private fun waitingForUserMessage(step: WaitingForUserStep): String =
    when (step) {
        WaitingForUserStep.UNINSTALL_CONFIRM -> "Confirm the uninstall in the system dialog."
        WaitingForUserStep.INSTALL_CONFIRM -> "Confirm the installation in the system dialog."
    }

@Composable
private fun ReadyToInstallSection(
    state: AppDetailsUiState,
    actionLabel: String,
    onInstall: () -> Unit,
    onRetryAsCleanInstall: () -> Unit,
) {
    val app = state.app
    when (val installStatus = state.installStatus) {
        is InstallStatus.Idle -> {
            val unmetDependencies = state.dependencies.filter { !it.installed }
            Button(
                onClick = onInstall,
                enabled = unmetDependencies.isEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(actionLabel)
            }
            if (unmetDependencies.isNotEmpty()) {
                val names = unmetDependencies.joinToString(", ") { it.app.displayName }
                HelperText("Install required dependencies first: $names.")
            }
        }

        is InstallStatus.Failed -> {
            FailedInstallSection(
                app = app,
                actionLabel = actionLabel,
                failure = installStatus,
                onInstall = onInstall,
                onRetryAsCleanInstall = onRetryAsCleanInstall,
            )
        }

        is InstallStatus.PreparingRollback -> {
            ManagerLinearProgress(progress = null)
            HelperText("Preserving the current version for rollback...")
        }

        is InstallStatus.Uninstalling -> {
            ManagerLinearProgress(progress = null)
            HelperText("Uninstalling the current version...")
        }

        is InstallStatus.Installing -> {
            ManagerLinearProgress(progress = null)
            HelperText("Installing...")
        }

        is InstallStatus.WaitingForUser -> {
            ManagerLinearProgress(progress = null)
            HelperText(waitingForUserMessage(installStatus.step))
        }

        is InstallStatus.RollingBack -> {
            ManagerLinearProgress(progress = null)
            HelperText("Install failed, restoring the previous version...")
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

// section 17 of the spec: a normal update failure offers an explicit, user-confirmed clean-install
// fallback with a data-loss warning. an app that already used clean install (videoapp patched, or a
// retry after this fallback) has nothing further to escalate to, so it only offers a plain retry
@Composable
private fun FailedInstallSection(
    app: AppProfile,
    actionLabel: String,
    failure: InstallStatus.Failed,
    onInstall: () -> Unit,
    onRetryAsCleanInstall: () -> Unit,
) {
    val reasonText = if (failure.rolledBack) "${failure.reason} The previous version was restored." else failure.reason
    StatusRow(icon = Icons.Filled.Error, tint = MaterialTheme.colorScheme.error, text = reasonText)

    if (app.installationMode == InstallationMode.CLEAN_INSTALL) {
        Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
            Text("Retry")
        }
        return
    }

    Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
        Text("Retry $actionLabel")
    }
    HelperText(
        "The application could not be updated normally. A clean installation can be attempted. " +
            "This may remove the app's local data.",
    )
    // outlined, not filled - this is a lossy fallback the user should have to notice is different
    // from the safe retry above, not a same-weight alternative
    OutlinedButton(onClick = onRetryAsCleanInstall, modifier = Modifier.fillMaxWidth()) {
        Text("Try clean install")
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
