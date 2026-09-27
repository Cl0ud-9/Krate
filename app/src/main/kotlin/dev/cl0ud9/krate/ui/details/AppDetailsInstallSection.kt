package dev.cl0ud9.krate.ui.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.model.WaitingForUserStep
import dev.cl0ud9.krate.ui.components.HelperText
import dev.cl0ud9.krate.ui.components.KrateLinearProgress
import dev.cl0ud9.krate.ui.components.ReopenPromptButton
import dev.cl0ud9.krate.ui.components.StatusRow
import dev.cl0ud9.krate.voice.Moment
import dev.cl0ud9.krate.voice.rememberKrateLeadIn

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
        state.isUpToDate -> "Reinstall"
        else -> "Update"
    }

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
    AnimatedContent(
        targetState = state.installStatus,
        contentKey = { it::class },
        transitionSpec = { fadeIn(tween(STATUS_FADE_MS)).togetherWith(fadeOut(tween(STATUS_FADE_MS))) },
        modifier = Modifier.animateContentSize(),
        label = "install-status",
    ) { installStatus ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InstallStatusContent(
                installStatus = installStatus,
                state = state,
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
                app = state.app,
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
                text = rememberKrateLeadIn(Moment.INSTALLED, "${state.app.displayName} is installed."),
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

// section 17 of the spec: a normal update failure offers an explicit, user-confirmed clean-install
// fallback with a data-loss warning. an app that already used clean install (an always-clean-install
// app, or a retry after this fallback) has nothing further to escalate to, so it only offers a plain retry
@Composable
private fun FailedInstallSection(
    app: AppProfile,
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

    // reinstalling from scratch only means something for a real failure on an app that's already
    // installed - not after the user said no, and not for a first install
    val offerReinstall = installed && !failure.userCancelled && app.installationMode != InstallationMode.CLEAN_INSTALL
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
        HelperText(UNINSTALL_FIRST_WARNING)
    }
}
