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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.InstallStatus
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.model.WaitingForUserStep

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
                app = state.app,
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
                icon = painterResource(R.drawable.ic_check_circle_rounded),
                tint = MaterialTheme.colorScheme.primary,
                text = "$actionLabel complete.",
            )
        }
    }
}

private fun waitingForUserMessage(step: WaitingForUserStep): String =
    when (step) {
        WaitingForUserStep.UNINSTALL_CONFIRM -> "Confirm the uninstall in the system dialog."
        WaitingForUserStep.INSTALL_CONFIRM -> "Confirm the installation in the system dialog."
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
    StatusRow(
        icon = painterResource(R.drawable.ic_error_rounded),
        tint = MaterialTheme.colorScheme.error,
        text = reasonText,
    )

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
