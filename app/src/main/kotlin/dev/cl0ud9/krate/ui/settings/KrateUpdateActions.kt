package dev.cl0ud9.krate.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.platform.selfupdate.KrateUpdateStatus
import dev.cl0ud9.krate.platform.selfupdate.SelfUpdateState
import dev.cl0ud9.krate.ui.components.HelperText
import dev.cl0ud9.krate.ui.components.KrateLinearProgress
import dev.cl0ud9.krate.ui.components.ReopenPromptButton
import dev.cl0ud9.krate.ui.util.DebouncedButtonState

// split out of SettingsScreen.kt purely to keep that file under detekt's per-file function-count
// threshold, same reasoning as AppDetailsInstallSection.kt's own split - this half owns everything
// that happens once About's "check for updates" has found a real update to install

// bundles the three self-update-related values About needs, instead of AboutRow/KrateUpdateSection
// each taking three more individual params - a plain param count problem, not a meaningful grouping
// on its own, but detekt's LongParameterList threshold is a real per-function limit regardless
internal data class KrateUpdateActions(
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
    status: KrateUpdateStatus.UpdateAvailable,
    selfUpdateState: SelfUpdateState?,
    onInstallUpdate: (String) -> Unit,
) {
    when (selfUpdateState) {
        null, is SelfUpdateState.DownloadFailed -> {
            if (selfUpdateState is SelfUpdateState.DownloadFailed) {
                KrateUpdateStatusRow(
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
            val fraction = selfUpdateState.fraction
            KrateLinearProgress(progress = fraction)
            HelperText(
                fraction?.let { "Downloading the update... ${(it * PERCENT).toInt()}%" } ?: "Downloading the update...",
            )
        }

        is SelfUpdateState.Installing ->
            SelfUpdateInstallingContent(
                installStatus = selfUpdateState.installStatus,
                retry = status.downloadUrl?.let { url -> { onInstallUpdate(url) } },
            )
    }
}

@Composable
private fun SelfUpdateStartButton(
    status: KrateUpdateStatus.UpdateAvailable,
    retrying: Boolean,
    onInstallUpdate: (String) -> Unit,
) {
    val downloadUrl = status.downloadUrl
    // the Play Protect steps come first; a retry after a failed download has already been through them
    var guide by rememberSaveable { mutableStateOf(false) }
    if (downloadUrl != null && guide && !retrying) {
        PlayProtectUpdateGuide(blocked = false, onUpdate = { onInstallUpdate(downloadUrl) })
    } else if (downloadUrl != null) {
        Button(
            onClick = { if (retrying) onInstallUpdate(downloadUrl) else guide = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (retrying) "Try again" else "Update now")
        }
    } else {
        val uriHandler = LocalUriHandler.current
        Button(onClick = { uriHandler.openUri(status.releaseUrl) }, modifier = Modifier.fillMaxWidth()) {
            Text("View release on GitHub")
        }
    }
}

@Composable
private fun SelfUpdateInstallingContent(
    installStatus: InstallStatus,
    // starts the update over; null when this release has no apk to download
    retry: (() -> Unit)?,
) {
    when (installStatus) {
        is InstallStatus.WaitingForUser -> {
            KrateLinearProgress(progress = null)
            HelperText(
                "Confirm the update in the system dialog. Android may run a Play Protect check first, which " +
                    "is normal for apps from outside the Play Store. Krate closes to finish updating; a " +
                    "notification lets you open it again.",
            )
            ReopenPromptButton()
        }

        is InstallStatus.Success -> {
            KrateUpdateStatusRow(
                icon = painterResource(R.drawable.ic_check_circle_rounded),
                badgeColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                text = "Update installed.",
            )
        }

        is InstallStatus.Failed -> {
            if (installStatus.blockedByPlayProtect && retry != null) {
                PlayProtectUpdateGuide(blocked = true, onUpdate = retry)
            } else {
                KrateUpdateStatusRow(
                    icon = painterResource(R.drawable.ic_error_rounded),
                    badgeColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    text = installStatus.reason,
                )
                if (retry != null) {
                    Button(onClick = retry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
                }
            }
        }

        // Installing, plus the clean-install-only states (Idle/PreparingRollback/Uninstalling/
        // RollingBack) that a plain in-place update never actually reaches - InstallStatus is one
        // shared sealed type, so the fallback still has to cover them
        else -> {
            KrateLinearProgress(progress = null)
            HelperText("Installing...")
        }
    }
}

private const val PERCENT = 100
