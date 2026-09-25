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
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import dev.cl0ud9.manager.ui.components.ReopenPromptButton
import dev.cl0ud9.manager.ui.components.SectionHeader
import dev.cl0ud9.manager.ui.components.StatusRow
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.voice.Moment
import dev.cl0ud9.manager.voice.rememberKrateLine

@Composable
internal fun DownloadSection(
    state: AppDetailsUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetryAsCleanInstall: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    val status = state.downloadStatus
    val actionLabel = actionLabelFor(state)
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
                        onCancelDownload = onCancelDownload,
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
    onCancelDownload: () -> Unit,
) {
    when (status) {
        is DownloadStatus.Idle -> IdleContent(state = state, onDownload = onDownload)

        is DownloadStatus.Downloading -> {
            val total = status.totalBytes
            val fraction = if (total != null && total > 0) status.bytesDownloaded / total.toFloat() else 0f
            ManagerLinearProgress(progress = if (total != null) fraction else null)
            HelperText(
                total?.let {
                    "Downloading ${formatMb(status.bytesDownloaded)} of ${formatMb(it)} MB " +
                        "(${(fraction * PERCENT).toInt()}%)"
                } ?: "Downloading ${formatMb(status.bytesDownloaded)} MB",
            )
            // a 170 MB download shouldn't be a commitment - the partial file is kept, so starting
            // again later resumes it
            OutlinedButton(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }

        is DownloadStatus.Verifying -> {
            ManagerLinearProgress(progress = null)
            HelperText("Checking the download is genuine...")
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
                text = "${rememberKrateLine(Moment.DOWNLOAD_FAILED, key = status.reason)} ${status.reason}",
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
                Text("Try again")
            }
        }
    }
}

// three real states, not two: up to date (Open + Redownload, unchanged), installed-but-behind
// (Open stays available - there's a real working app right there - alongside the actual Update
// action, matching how any app store pairs Open with a pending update instead of hiding one behind
// the other), and genuinely not installed (plain Install button, nothing to open). A prominent
// "Download" button for an app that's actually sitting on the device would wrongly suggest
// otherwise - that used to be true for BOTH non-up-to-date cases, hiding Open even when something
// installed and working was right there
@Composable
private fun IdleContent(
    state: AppDetailsUiState,
    onDownload: () -> Unit,
) {
    val selected = state.selectedArtifact
    val installed = state.installedVersionName != null
    val awaitingUninstallConfirm =
        state.installStatus is InstallStatus.WaitingForUser &&
            state.installStatus.step == WaitingForUserStep.UNINSTALL_CONFIRM
    val uninstalling = state.installStatus is InstallStatus.Uninstalling || awaitingUninstallConfirm

    when {
        state.isRollback -> RollbackContent(state = state, uninstalling = uninstalling, onDownload = onDownload)

        state.isUpToDate -> {
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
        }

        installed -> {
            StatusRow(
                icon = painterResource(R.drawable.ic_system_update_alt_rounded),
                tint = MaterialTheme.colorScheme.primary,
                text = "Update available: ${selected?.buildDescription()}.",
            )
            if (uninstalling) {
                UninstallingStatus(installStatus = state.installStatus)
            } else {
                InstalledNotUpToDateActions(state = state, onDownload = onDownload)
            }
        }

        else -> {
            Button(onClick = onDownload, enabled = selected != null, modifier = Modifier.fillMaxWidth()) {
                Text("Install")
            }
            if (selected == null) {
                HelperText("Not yet available for download.")
            }
        }
    }
    IdleFootnotes(state = state, uninstalling = uninstalling)
}

// an older retained build picked in version history - Open stays available, the action rolls back
@Composable
private fun RollbackContent(
    state: AppDetailsUiState,
    uninstalling: Boolean,
    onDownload: () -> Unit,
) {
    StatusRow(
        icon = rememberVectorPainter(Icons.Filled.History),
        tint = MaterialTheme.colorScheme.primary,
        text = "Older version selected: ${state.selectedArtifact?.buildDescription()}.",
    )
    if (uninstalling) {
        UninstallingStatus(installStatus = state.installStatus)
    } else {
        InstalledNotUpToDateActions(state = state, onDownload = onDownload)
    }
    if (state.requiresUninstall) {
        HelperText(UNINSTALL_FIRST_WARNING)
    }
}

// independent of which branch rendered - a real pending update and a diverged install aren't
// mutually exclusive, so this can appear alongside either "Up to date" or "Update available"
@Composable
private fun IdleFootnotes(
    state: AppDetailsUiState,
    uninstalling: Boolean,
) {
    if (state.isDiverged) {
        HelperText(
            "Installed version changed from ${state.effectiveBaseline?.versionName} to " +
                "${state.installedVersionName} outside the manager.",
        )
    }
    val failure = state.installStatus as? InstallStatus.Failed
    if (!uninstalling && failure != null) {
        FailureStatusRow(failure = failure)
    }
}

// Open stacked above the real Update/Roll back action - same pairing UpToDateActions uses for
// Open+Redownload, just with the actual CTA instead of a redownload of the same thing
@Composable
private fun InstalledNotUpToDateActions(
    state: AppDetailsUiState,
    onDownload: () -> Unit,
) {
    val selected = state.selectedArtifact
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OpenAppButton(packageName = state.app.packageName, secondary = true)
        Button(onClick = onDownload, enabled = selected != null, modifier = Modifier.fillMaxWidth()) {
            Text(actionLabelFor(state))
        }
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
// secondary = tonal instead of filled, for when another action on the card is the main one
@Composable
internal fun OpenAppButton(
    packageName: String,
    secondary: Boolean = false,
) {
    val context = LocalContext.current
    val launchIntent =
        remember(packageName) { context.packageManager.getLaunchIntentForPackage(packageName) }
    if (launchIntent != null) {
        val colors =
            if (secondary) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                ButtonDefaults.buttonColors()
            }
        Button(
            onClick = { context.startActivity(launchIntent) },
            modifier = Modifier.fillMaxWidth(),
            colors = colors,
        ) {
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
    if (installStatus is InstallStatus.WaitingForUser) ReopenPromptButton()
}

private const val BYTES_PER_MB = 1024 * 1024

private fun formatMb(bytes: Long): String = "%.1f".format(bytes / BYTES_PER_MB.toFloat())

private const val PERCENT = 100
