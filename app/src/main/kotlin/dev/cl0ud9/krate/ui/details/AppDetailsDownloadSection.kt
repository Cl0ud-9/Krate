package dev.cl0ud9.krate.ui.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.DownloadStatus
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.domain.model.WaitingForUserStep
import dev.cl0ud9.krate.ui.components.HelperText
import dev.cl0ud9.krate.ui.components.KrateLinearProgress
import dev.cl0ud9.krate.ui.components.ProgressCaption
import dev.cl0ud9.krate.ui.components.SectionHeader
import dev.cl0ud9.krate.ui.components.StatusRow
import dev.cl0ud9.krate.ui.components.downloadFraction
import dev.cl0ud9.krate.ui.components.fadeThrough
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.navigation.heroGlow
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.voice.Moment
import dev.cl0ud9.krate.voice.rememberKrateLeadIn

@Suppress("LongParameterList")
@Composable
internal fun DownloadSection(
    state: AppDetailsUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetryAsCleanInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    onBackToLatest: () -> Unit,
) {
    // Play Protect's guide stands between these actions and an app it stops
    val gate = rememberPlayProtectGate(state, onDownload, onInstall)
    // boxed in a card like every other detail section, instead of sitting bare on the screen background
    Card(
        modifier = Modifier.fillMaxWidth().glassRim(ShapeCache.rounded16),
        shape = ShapeCache.rounded16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heroGlow().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(title = "Get this app", icon = rememberVectorPainter(Icons.Filled.Download))
            // folds in and out, since it comes and goes as an install is stopped or gets through
            AnimatedVisibility(
                visible = gate.headsUp,
                enter = fadeIn() + expandVertically(),
                exit =
                    fadeOut() + shrinkVertically(),
            ) {
                HelperText("Play Protect stopped this app before. Krate shows you the way past it when you go ahead.")
            }
            // fades through each real change (Install -> downloading -> ... and, once removed, Open -> Install)
            // while the card eases to its new height; each side keeps the state it was drawn with, so the outgoing
            // buttons never flip mid-fade. A progress tick has the same phase, so it updates in place
            AnimatedContent(
                targetState = state,
                contentKey = ::downloadPhase,
                transitionSpec = fadeThrough(),
                contentAlignment = Alignment.TopStart,
                label = "download-status",
            ) { shown ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CompositionLocalProvider(LocalPlayProtectGuide provides gate.showGuide) {
                        DownloadStatusContent(
                            status = shown.downloadStatus,
                            state = shown,
                            actionLabel = actionLabelFor(shown),
                            onDownload = gate.download,
                            onInstall = gate.install,
                            onRetryAsCleanInstall = onRetryAsCleanInstall,
                            onCancelDownload = onCancelDownload,
                            onBackToLatest = onBackToLatest,
                        )
                    }
                }
            }
        }
    }
}

// what the card is showing, coarsely: a download's own steps, or for an idle one which set of buttons it offers
private fun downloadPhase(state: AppDetailsUiState): Any {
    val status = state.downloadStatus
    return when {
        status !is DownloadStatus.Idle -> status::class
        state.isRollback || state.isSwitch -> "switch"
        state.isUpToDate -> "current"
        state.installedVersionName != null -> "behind"
        else -> "new"
    }
}

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
    onBackToLatest: () -> Unit,
) {
    when (status) {
        is DownloadStatus.Idle -> IdleContent(state = state, onDownload = onDownload, onBackToLatest = onBackToLatest)

        is DownloadStatus.Downloading -> {
            val fraction = downloadFraction(status.bytesDownloaded, status.totalBytes)
            KrateLinearProgress(progress = fraction)
            ProgressCaption(downloadingLabel(status.bytesDownloaded, status.totalBytes, fraction ?: 0f))
            // a 170 MB download shouldn't be a commitment - the partial file is kept, so starting
            // again later resumes it
            OutlinedButton(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }

        is DownloadStatus.Verifying -> {
            KrateLinearProgress(progress = null)
            HelperText("Making sure the download is genuine...")
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
                text = rememberKrateLeadIn(Moment.DOWNLOAD_FAILED, status.reason, key = status.reason),
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

// three real states, not two: up to date (Open + Reinstall), installed-but-behind
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
    onBackToLatest: () -> Unit,
) {
    val selected = state.selectedArtifact
    val installed = state.installedVersionName != null
    val awaitingUninstallConfirm =
        state.installStatus is InstallStatus.WaitingForUser &&
            state.installStatus.step == WaitingForUserStep.UNINSTALL_CONFIRM
    val uninstalling = state.installStatus is InstallStatus.Uninstalling || awaitingUninstallConfirm

    when {
        state.isRollback || state.isSwitch ->
            RollbackContent(state, uninstalling, onDownload, onBackToLatest)

        state.isUpToDate -> {
            val colorScheme = MaterialTheme.colorScheme
            StatusRow(
                icon = painterResource(R.drawable.ic_check_circle_rounded),
                // the same colour as the install's own success step, so settling into this doesn't change it
                tint = if (state.justInstalled) colorScheme.primary else colorScheme.tertiary,
                text = installedStatusText(state),
            )
            UninstallSwap(uninstalling, state.installStatus) {
                UpToDateActions(
                    packageName = state.app.packageName,
                    label = downloadLabelFor(state),
                    onDownload = onDownload,
                )
            }
        }

        installed -> {
            StatusRow(
                icon = painterResource(R.drawable.ic_system_update_alt_rounded),
                tint = MaterialTheme.colorScheme.primary,
                text =
                    rememberUpdateAvailableText(
                        state.installedVersionName,
                        selected?.versionName,
                        "Update available: ${selected?.buildDescription()}.",
                    ),
            )
            UninstallSwap(uninstalling, state.installStatus) {
                InstalledNotUpToDateActions(state = state, onDownload = onDownload)
            }
        }

        else -> {
            Button(onClick = onDownload, enabled = selected != null, modifier = Modifier.fillMaxWidth()) {
                Text(downloadLabelFor(state))
            }
            if (selected == null) {
                HelperText("Not yet available for download.")
            }
        }
    }
    IdleFootnotes(state = state, uninstalling = uninstalling)
}

@Composable
private fun installedStatusText(state: AppDetailsUiState): String =
    when {
        state.justInstalled -> rememberInstalledText(state.app)
        state.signedDifferently -> "Installed from somewhere else."
        else -> "Up to date."
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
                "${state.installedVersionName} outside Krate.",
        )
    }
    // said before the download, not only once it fails
    if (state.signedDifferently && !uninstalling) HelperText(SIGNED_DIFFERENTLY_WARNING)
    val failure = state.installStatus as? InstallStatus.Failed
    if (!uninstalling && failure != null) {
        FailureStatusRow(failure = failure)
        if (state.installed != null && failure.mayBeAppLock) HelperText(APP_LOCK_HINT)
    }
}

// Open stacked above the real Update/Roll back action - same pairing UpToDateActions uses for
// Open+Reinstall, just with the actual CTA instead of a reinstall of the same thing
@Composable
private fun InstalledNotUpToDateActions(
    state: AppDetailsUiState,
    onDownload: () -> Unit,
) {
    val selected = state.selectedArtifact
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OpenAppButton(packageName = state.app.packageName, secondary = true)
        Button(onClick = onDownload, enabled = selected != null, modifier = Modifier.fillMaxWidth()) {
            Text(downloadLabelFor(state))
        }
    }
}

// Open (primary) stacked above a secondary-toned Reinstall, both full width - if the installed
// package can actually be launched. Falls back to a lone full-width Reinstall for the rare case
// of an installed package with no launcher activity (a pure library/dependency app, e.g. microG RE)
@Composable
private fun UpToDateActions(
    packageName: String,
    label: String,
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
                Text(label)
            }
        }
    } else {
        Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
            Text(label)
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
