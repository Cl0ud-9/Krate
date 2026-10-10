package dev.cl0ud9.krate.ui.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.ui.components.HelperText
import dev.cl0ud9.krate.ui.components.KrateLinearProgress
import dev.cl0ud9.krate.ui.components.ReopenPromptButton
import dev.cl0ud9.krate.ui.components.fadeThrough
import kotlinx.coroutines.delay

// the buttons fade through to the uninstall's progress and back, instead of swapping in a single frame
@Composable
internal fun UninstallSwap(
    uninstalling: Boolean,
    installStatus: InstallStatus,
    actions: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = uninstalling,
        transitionSpec = fadeThrough(),
        contentAlignment = Alignment.TopStart,
        label = "uninstall-swap",
    ) { shown ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (shown) UninstallingStatus(installStatus = installStatus) else actions()
        }
    }
}

// installStatus is shared with the install flow elsewhere on this screen, but Uninstalling/
// WaitingForUser(UNINSTALL_CONFIRM) are only ever emitted by the uninstall flow itself, so reading
// them here is unambiguous. A successful uninstall isn't shown explicitly: refresh() flips
// installedVersionName to null, which removes this whole control and reveals the Download button -
// the same feedback any uninstall (from here or from system Settings) gives
@Composable
internal fun UninstallingStatus(installStatus: InstallStatus) {
    KrateLinearProgress(progress = null)
    HelperText(
        if (installStatus is InstallStatus.Uninstalling) {
            "Uninstalling..."
        } else {
            "Confirm the uninstall in the system dialog."
        },
    )
    if (installStatus is InstallStatus.WaitingForUser) {
        ReopenPromptButton()
        // a phone's app lock can hold the dialog back without a word, so after a while it gets a mention
        var stillWaiting by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(APP_LOCK_HINT_DELAY_MS)
            stillWaiting = true
        }
        if (stillWaiting) {
            HelperText(
                "No dialog? If this app is locked with your phone's app lock, unlock it, then show the prompt again.",
            )
        }
    }
}

private const val APP_LOCK_HINT_DELAY_MS = 6_000L
