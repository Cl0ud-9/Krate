package dev.cl0ud9.manager.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.domain.model.InstallStatus
import dev.cl0ud9.manager.platform.selfupdate.ManagerUpdateStatus
import dev.cl0ud9.manager.platform.selfupdate.SelfUpdateState
import dev.cl0ud9.manager.ui.components.HelperText
import dev.cl0ud9.manager.ui.components.ManagerLinearProgress
import dev.cl0ud9.manager.ui.util.DebouncedButtonState

// split out of SettingsScreen.kt purely to keep that file under detekt's per-file function-count
// threshold, same reasoning as AppDetailsInstallSection.kt's own split - this half owns everything
// that happens once About's "check for updates" has found a real update to install

// bundles the three self-update-related values About needs, instead of AboutRow/ManagerUpdateSection
// each taking three more individual params - a plain param count problem, not a meaningful grouping
// on its own, but detekt's LongParameterList threshold is a real per-function limit regardless
internal data class ManagerUpdateActions(
    val checkForUpdateState: DebouncedButtonState,
    val selfUpdateState: SelfUpdateState?,
    val onInstallUpdate: (String) -> Unit,
)

// replaces opening the GitHub release page in a browser with an actual in-app download + install -
// the system's own PackageInstaller confirmation dialog this triggers IS the "prompt to update"
// that was asked for. Falls back to the old "view on GitHub" behavior only if this particular
// release genuinely has no .apk asset attached (shouldn't happen for a release this repo's own
// pipeline built, but a hand-created release could omit it)
@Composable
internal fun SelfUpdateAction(
    status: ManagerUpdateStatus.UpdateAvailable,
    selfUpdateState: SelfUpdateState?,
    onInstallUpdate: (String) -> Unit,
) {
    when (selfUpdateState) {
        null, is SelfUpdateState.DownloadFailed -> {
            if (selfUpdateState is SelfUpdateState.DownloadFailed) {
                ManagerUpdateStatusRow(
                    icon = painterResource(R.drawable.ic_error_rounded),
                    badgeColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    text = selfUpdateState.reason,
                )
            }
            SelfUpdateStartButton(
                status = status,
                retrying = selfUpdateState != null,
                onInstallUpdate = onInstallUpdate,
            )
        }

        is SelfUpdateState.Downloading -> {
            ManagerLinearProgress(progress = null)
            HelperText("Downloading the update...")
        }

        is SelfUpdateState.Installing -> SelfUpdateInstallingContent(installStatus = selfUpdateState.installStatus)
    }
}

@Composable
private fun SelfUpdateStartButton(
    status: ManagerUpdateStatus.UpdateAvailable,
    retrying: Boolean,
    onInstallUpdate: (String) -> Unit,
) {
    val downloadUrl = status.downloadUrl
    if (downloadUrl != null) {
        Button(onClick = { onInstallUpdate(downloadUrl) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (retrying) "Retry download" else "Download & install")
        }
    } else {
        val uriHandler = LocalUriHandler.current
        Button(onClick = { uriHandler.openUri(status.releaseUrl) }, modifier = Modifier.fillMaxWidth()) {
            Text("View release on GitHub")
        }
    }
}

@Composable
private fun SelfUpdateInstallingContent(installStatus: InstallStatus) {
    when (installStatus) {
        is InstallStatus.WaitingForUser -> {
            ManagerLinearProgress(progress = null)
            HelperText("Confirm the update in the system dialog.")
        }

        is InstallStatus.Success -> {
            ManagerUpdateStatusRow(
                icon = painterResource(R.drawable.ic_check_circle_rounded),
                badgeColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                text = "Update installed.",
            )
        }

        is InstallStatus.Failed -> {
            ManagerUpdateStatusRow(
                icon = painterResource(R.drawable.ic_error_rounded),
                badgeColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                text = installStatus.reason,
            )
        }

        // Installing, plus the clean-install-only states (Idle/PreparingRollback/Uninstalling/
        // RollingBack) that a plain in-place update never actually reaches - InstallStatus is one
        // shared sealed type, so the fallback still has to cover them
        else -> {
            ManagerLinearProgress(progress = null)
            HelperText("Installing...")
        }
    }
}
