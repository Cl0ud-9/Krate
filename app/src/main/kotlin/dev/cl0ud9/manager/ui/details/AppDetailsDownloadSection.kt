package dev.cl0ud9.manager.ui.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.domain.model.DownloadStatus
import dev.cl0ud9.manager.domain.model.InstallStatus
import dev.cl0ud9.manager.domain.model.WaitingForUserStep
import dev.cl0ud9.manager.ui.components.HelperText
import dev.cl0ud9.manager.ui.components.ManagerLinearProgress
import dev.cl0ud9.manager.ui.components.SectionHeader
import dev.cl0ud9.manager.ui.components.StatusRow
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
    val status = state.downloadStatus
    val actionLabel = actionLabelFor(state.selectedArtifact?.versionName, state.installedVersionName)
    // boxed in a card like every other detail section, instead of sitting bare on the screen background
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(title = "Get this app", icon = rememberVectorPainter(Icons.Filled.Download))
            // crossfades between states (Idle -> Downloading -> Verifying -> ...) instead of the
            // content just swapping instantly - contentKey groups by the status's own class, not
            // its full value, so a Downloading progress tick (a genuinely new instance every time,
            // bytesDownloaded included) updates in place rather than re-triggering the transition
            AnimatedContent(
                targetState = status,
                contentKey = { it::class },
                transitionSpec = {
                    (fadeIn(tween(STATUS_FADE_MS)))
                        .togetherWith(fadeOut(tween(STATUS_FADE_MS)))
                },
                modifier = Modifier.animateContentSize(),
                label = "download-status",
            ) { currentStatus ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DownloadStatusContent(
                        status = currentStatus,
                        state = state,
                        actionLabel = actionLabel,
                        onDownload = onDownload,
                        onInstall = onInstall,
                        onRetryAsCleanInstall = onRetryAsCleanInstall,
                    )
                }
            }
        }
    }
}

internal const val STATUS_FADE_MS = 220

@Suppress("LongParameterList")
@Composable
private fun DownloadStatusContent(
    status: DownloadStatus,
    state: AppDetailsUiState,
    actionLabel: String,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetryAsCleanInstall: () -> Unit,
) {
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
            StatusRow(
                icon = painterResource(R.drawable.ic_error_rounded),
                tint = MaterialTheme.colorScheme.error,
                text = status.reason,
            )
            // a failed redownload attempt used to hide the Open button entirely, even when the
            // already-installed app is perfectly fine - isUpToDate here is the same check IdleContent
            // uses, just also applied to the Failed branch, so the app stays reachable while the
            // retry option sits alongside it instead of replacing it
            if (state.isUpToDate) {
                OpenAppButton(packageName = state.app.packageName)
            }
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    if (state.isUpToDate) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
            ) {
                Text("Retry download")
            }
        }
    }
}

// installedVersionName already matching the catalog's latest means there is nothing pending - a
// prominent "Download" button here would wrongly suggest otherwise. The primary action is "Open"
// (like any app store's already-installed state); Redownload and Uninstall are both secondary, so
// they sit side by side below it instead of each getting their own full-width row stacked one under
// the other - three full-width controls in a column read as heavier/more repetitive than the same
// two secondary actions paired in one row under the one action that actually matters
@Composable
private fun IdleContent(
    state: AppDetailsUiState,
    onDownload: () -> Unit,
) {
    val selected = state.selectedArtifact
    val upToDate = state.isUpToDate
    val awaitingUninstallConfirm =
        state.installStatus is InstallStatus.WaitingForUser &&
            state.installStatus.step == WaitingForUserStep.UNINSTALL_CONFIRM
    val uninstalling = state.installStatus is InstallStatus.Uninstalling || awaitingUninstallConfirm

    if (upToDate) {
        // selected is necessarily non-null here: upToDate can only be true when its versionName
        // matched a real installedVersionName
        StatusRow(
            icon = painterResource(R.drawable.ic_check_circle_rounded),
            tint = MaterialTheme.colorScheme.tertiary,
            text = "Up to date.",
        )
        if (uninstalling) {
            UninstallingStatus(installStatus = state.installStatus)
        } else {
            UpToDateActions(packageName = state.app.packageName, onDownload = onDownload)
        }
    } else {
        Button(onClick = onDownload, enabled = selected != null, modifier = Modifier.fillMaxWidth()) {
            Text("Download")
        }
        if (selected == null) {
            HelperText("Not yet available for download.")
        }
        if (state.installedVersionName != null && uninstalling) {
            UninstallingStatus(installStatus = state.installStatus)
        }
    }
    if (!uninstalling && state.installStatus is InstallStatus.Failed) {
        StatusRow(
            icon = painterResource(R.drawable.ic_error_rounded),
            tint = MaterialTheme.colorScheme.error,
            text = state.installStatus.reason,
        )
    }
}

// Open (primary) stacked above a secondary-toned Redownload, both full width - if the installed
// package can actually be launched. Falls back to a lone full-width Redownload for the rare case
// of an installed package with no launcher activity (a pure library/dependency app, e.g. microG RE)
@Composable
private fun UpToDateActions(
    packageName: String,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    val launchIntent =
        remember(packageName) { context.packageManager.getLaunchIntentForPackage(packageName) }
    if (launchIntent != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OpenAppButton(packageName = packageName)
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
            ) {
                Text("Redownload")
            }
        }
    } else {
        Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
            Text("Redownload")
        }
    }
}

// shared by UpToDateActions and the Failed branch above, plus InstallStatus.Success in
// AppDetailsInstallSection.kt (same package) - renders nothing for a package with no launcher
// activity (a pure library dependency, e.g. microG RE), same fallback every call site needs
@Composable
internal fun OpenAppButton(packageName: String) {
    val context = LocalContext.current
    val launchIntent =
        remember(packageName) { context.packageManager.getLaunchIntentForPackage(packageName) }
    if (launchIntent != null) {
        Button(onClick = { context.startActivity(launchIntent) }, modifier = Modifier.fillMaxWidth()) {
            Text("Open")
        }
    }
}

// installStatus is shared with the install flow elsewhere on this screen, but Uninstalling/
// WaitingForUser(UNINSTALL_CONFIRM) are only ever emitted by the uninstall flow itself, so reading
// them here is unambiguous. A successful uninstall isn't shown explicitly: refresh() flips
// installedVersionName to null, which removes this whole control and reveals the Download button -
// the same feedback any uninstall (from here or from system Settings) gives
@Composable
private fun UninstallingStatus(installStatus: InstallStatus) {
    ManagerLinearProgress(progress = null)
    HelperText(
        if (installStatus is InstallStatus.Uninstalling) {
            "Uninstalling..."
        } else {
            "Confirm the uninstall in the system dialog."
        },
    )
}

private const val BYTES_PER_MB = 1024 * 1024

private fun formatMb(bytes: Long): String = "%.1f".format(bytes / BYTES_PER_MB.toFloat())
