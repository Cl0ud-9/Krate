package dev.cl0ud9.krate.ui.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.DownloadStatus
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.domain.model.WaitingForUserStep
import dev.cl0ud9.krate.platform.packageinfo.joinAreas
import dev.cl0ud9.krate.platform.packageinfo.newSensitiveAreas
import dev.cl0ud9.krate.ui.components.HelperText
import dev.cl0ud9.krate.ui.components.KrateLinearProgress
import dev.cl0ud9.krate.ui.components.ReopenPromptButton
import dev.cl0ud9.krate.ui.components.StatusRow
import dev.cl0ud9.krate.ui.components.fadeThrough
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.voice.Moment
import dev.cl0ud9.krate.voice.rememberKrateLeadIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// section 16 of the spec: the ui shows Install, Update, Reinstall or Roll back based on real device
// state compared against whichever build is currently selected (App Details' version history lets
// that be an older retained one, not always the latest), not just the installation mode. "Reinstall"
// when the selected build is no newer than what Krate last installed - there is nothing to
// update to. Mode doesn't drive the label at all: the Installation card below already explains the
// clean-install mechanics separately, so this only needs to answer "is there something new"
internal fun actionLabelFor(state: AppDetailsUiState): String =
    when {
        state.installed == null -> "Install"
        state.isRollback -> "Roll back"
        state.isSwitch -> "Switch to this build"
        state.signedDifferently -> "Replace"
        state.isUpToDate -> "Reinstall"
        else -> "Update"
    }

// the download button: the whole action when one tap installs too, otherwise just the download (the rest is its own
// step, with its own warning)
internal fun downloadLabelFor(state: AppDetailsUiState): String =
    if (state.installsInPlace) actionLabelFor(state) else "Download"

internal const val APP_LOCK_HINT = "If this app is locked with your phone's app lock, unlock it and try again."

internal const val SIGNED_DIFFERENTLY_WARNING =
    "The copy on this phone is signed with a different key, so Android needs it uninstalled before this one " +
        "can go on. Its data on this device will be erased."

internal const val UNINSTALL_FIRST_WARNING =
    "This is older than the installed version, so Android needs the app uninstalled first. " +
        "Its data on this device will be erased."

// split out of AppDetailsDownloadSection.kt purely to keep that file under detekt's per-file
// function-count threshold - this half owns everything that happens once a download has reached
// ReadyToInstall, the other half owns the download itself
@Composable
internal fun ReadyToInstallSection(
    state: AppDetailsUiState,
    actionLabel: String,
    onInstall: () -> Unit,
    onRetryAsCleanInstall: () -> Unit,
) {
    // each side keeps the state it was drawn with, so the outgoing step never changes mid-fade
    AnimatedContent(
        targetState = state,
        contentKey = { it.installStatus::class },
        transitionSpec = fadeThrough(),
        contentAlignment = Alignment.TopStart,
        label = "install-status",
    ) { shown ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InstallStatusContent(
                installStatus = shown.installStatus,
                state = shown,
                actionLabel = actionLabel,
                onInstall = onInstall,
                onRetryAsCleanInstall = onRetryAsCleanInstall,
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun InstallStatusContent(
    installStatus: InstallStatus,
    state: AppDetailsUiState,
    actionLabel: String,
    onInstall: () -> Unit,
    onRetryAsCleanInstall: () -> Unit,
) {
    when (installStatus) {
        is InstallStatus.Idle -> ReadyToInstallContent(state = state, actionLabel = actionLabel, onInstall = onInstall)

        is InstallStatus.Failed -> {
            FailedInstallSection(
                fromScratch = state.installsFromScratch,
                installed = state.installed != null,
                failure = installStatus,
                onInstall = onInstall,
                onRetryAsCleanInstall = onRetryAsCleanInstall,
            )
        }

        is InstallStatus.PreparingRollback -> {
            KrateLinearProgress(progress = null)
            HelperText("Keeping a copy of the current version...")
        }

        is InstallStatus.Uninstalling -> {
            KrateLinearProgress(progress = null)
            HelperText("Uninstalling the current version...")
        }

        is InstallStatus.Installing -> {
            KrateLinearProgress(progress = null)
            HelperText("Installing...")
        }

        is InstallStatus.WaitingForUser -> {
            KrateLinearProgress(progress = null)
            HelperText(waitingForUserMessage(installStatus.step))
            ReopenPromptButton()
        }

        is InstallStatus.RollingBack -> {
            KrateLinearProgress(progress = null)
            HelperText("The install failed. Restoring the previous version...")
        }

        is InstallStatus.Success -> {
            StatusRow(
                icon = painterResource(R.drawable.ic_check_circle_rounded),
                tint = MaterialTheme.colorScheme.primary,
                text = rememberInstalledText(state.app),
            )
            // previously nothing followed this message - the app was reachable again only after
            // leaving and re-entering App Details (which re-derives downloadStatus back to Idle and
            // shows UpToDateActions instead). OpenAppButton renders nothing for a package with no
            // launcher activity, same fallback UpToDateActions already relies on
            OpenAppButton(packageName = state.app.packageName)
        }
    }
}

private fun waitingForUserMessage(step: WaitingForUserStep): String =
    when (step) {
        WaitingForUserStep.UNINSTALL_CONFIRM -> "Confirm the uninstall in the system dialog."
        WaitingForUserStep.INSTALL_CONFIRM -> "Confirm the install in the system dialog."
    }

// a failed in-place update offers a user-confirmed install from scratch, with its data-loss warning; an attempt
// that already uninstalled first (an always-clean-install app, or a rollback) has nothing further to fall back to
@Composable
private fun FailedInstallSection(
    fromScratch: Boolean,
    installed: Boolean,
    failure: InstallStatus.Failed,
    onInstall: () -> Unit,
    onRetryAsCleanInstall: () -> Unit,
) {
    val reasonText = if (failure.rolledBack) "${failure.reason} The previous version was restored." else failure.reason
    // saying no in the system dialog isn't a mishap, so a cancel gets no headline
    val text =
        if (failure.userCancelled) {
            reasonText
        } else {
            rememberKrateLeadIn(Moment.INSTALL_FAILED, reasonText, key = failure.reason)
        }
    FailureStatusRow(failure = failure, text = text)
    if (installed && failure.mayBeAppLock) HelperText(APP_LOCK_HINT)
    // a reinstall from scratch would uninstall the app first and then be stopped all the same; the way past Play
    // Protect is the only useful step
    val showGuide = LocalPlayProtectGuide.current
    if (failure.blockedByPlayProtect && showGuide != null) {
        Button(onClick = showGuide, modifier = Modifier.fillMaxWidth()) { Text("Show me how") }
        TextButton(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
        return
    }

    // only for a real failure of an in-place update: not after the user said no, not for a first install
    val offerReinstall = installed && !failure.userCancelled && !fromScratch
    if (!offerReinstall) {
        Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
            Text("Try again")
        }
        return
    }

    Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
        Text("Try again")
    }
    HelperText(
        "Reinstalling from scratch uninstalls the current version first, so the app's data on this " +
            "device is erased.",
    )
    // outlined, not filled - this is a lossy fallback the user should have to notice is different
    // from the safe retry above, not a same-weight alternative
    OutlinedButton(onClick = onRetryAsCleanInstall, modifier = Modifier.fillMaxWidth()) {
        Text("Reinstall from scratch")
    }
}

// a failure in error red, a user's own cancel in a neutral tone - saying no isn't something to fix
@Composable
internal fun FailureStatusRow(
    failure: InstallStatus.Failed,
    text: String = failure.reason,
) {
    val cancelled = failure.userCancelled
    StatusRow(
        icon = painterResource(if (cancelled) R.drawable.ic_info_rounded else R.drawable.ic_error_rounded),
        tint = if (cancelled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        text = text,
    )
}

// the download is done and verified - otherwise identical to the not-yet-downloaded screen, which
// also shows a lone button, so it says so
// what this build can reach that the installed one can't, said beside Install so nothing new is a surprise. Only
// information: Android still asks before the app uses most of it
@Composable
private fun NewPermissionsNote(state: AppDetailsUiState) {
    val context = LocalContext.current
    val file = (state.downloadStatus as? DownloadStatus.ReadyToInstall)?.filePath
    val areas by produceState(emptyList<String>(), file, state.installed) {
        if (file != null && state.installed != null) {
            value = withContext(Dispatchers.IO) { newSensitiveAreas(context, state.app.packageName, file) }
        }
    }
    if (areas.isEmpty()) return
    Surface(shape = ShapeCache.rounded12, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painterResource(R.drawable.ic_security_rounded),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text =
                    "This version can also use ${joinAreas(areas)}. Android asks you before ${state.app.displayName} " +
                        "uses most of these.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun ReadyToInstallContent(
    state: AppDetailsUiState,
    actionLabel: String,
    onInstall: () -> Unit,
) {
    val unmetDependencies = state.dependencies.filter { !it.installed }
    StatusRow(
        icon = painterResource(R.drawable.ic_check_circle_rounded),
        tint = MaterialTheme.colorScheme.tertiary,
        text = rememberKrateLeadIn(Moment.DOWNLOADED, "Downloaded and checked, ready to install."),
    )
    NewPermissionsNote(state)
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
    if (state.requiresUninstall) {
        HelperText(if (state.signedDifferently) SIGNED_DIFFERENTLY_WARNING else UNINSTALL_FIRST_WARNING)
    }
}
