package dev.cl0ud9.krate.ui.details

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.domain.model.isTracked
import dev.cl0ud9.krate.platform.selfupdate.PlayProtect
import dev.cl0ud9.krate.platform.selfupdate.PlayProtectFlags
import dev.cl0ud9.krate.platform.workers.PlayProtectReminderWorker
import dev.cl0ud9.krate.ui.components.PAUSE_STEP_DETAIL
import dev.cl0ud9.krate.ui.components.PlayProtectGuideSheet
import dev.cl0ud9.krate.ui.components.PlayProtectGuideText

// opens the Play Protect guide for this page's app, for the failure row's "Show me how"
internal val LocalPlayProtectGuide = staticCompositionLocalOf<(() -> Unit)?> { null }

private const val ACTION_INSTALL = "install"
private const val ACTION_DOWNLOAD = "download"

// the page's install and one-tap download, put through Play Protect's guide where it stops (or has stopped) the app
internal class PlayProtectGate(
    val download: () -> Unit,
    val install: () -> Unit,
    val showGuide: () -> Unit,
    // Play Protect stopped this app before, and there's something to install
    val headsUp: Boolean,
)

// an app Play Protect stopped before goes through the guide first; a block on screen opens it by itself, once. Scanning
// already paused for this app means the guide has done its job, so the actions go straight through
@Composable
internal fun rememberPlayProtectGate(
    state: AppDetailsUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
): PlayProtectGate {
    val context = LocalContext.current
    val app = state.app
    var guide by rememberSaveable { mutableStateOf<String?>(null) }
    val changes = PlayProtect.changes.intValue
    val flagged =
        remember(app.packageName, changes, state.installStatus) { PlayProtectFlags.isFlagged(context, app.packageName) }
    val pausedForIt = remember(app.packageName, changes) { PlayProtect.pausedForPackage(context) == app.packageName }
    val blocked = (state.installStatus as? InstallStatus.Failed)?.takeIf { it.blockedByPlayProtect }
    var shownFor by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(blocked) {
        val key = blocked?.let { System.identityHashCode(it) } ?: return@LaunchedEffect
        if (shownFor != key) {
            shownFor = key
            guide = ACTION_INSTALL
        }
    }
    val holdBack = flagged && !pausedForIt
    val gate =
        PlayProtectGate(
            download = { if (holdBack && state.installsInPlace) guide = ACTION_DOWNLOAD else onDownload() },
            install = { if (holdBack) guide = ACTION_INSTALL else onInstall() },
            showGuide = { guide = ACTION_INSTALL },
            headsUp = flagged && blocked == null && (state.installed == null || state.pendingUpdate != null),
        )
    guide?.let { action ->
        val proceed = if (action == ACTION_DOWNLOAD) onDownload else onInstall
        PlayProtectGuideSheet(
            text = appGuideText(app, actionLabelFor(state), stoppedNow = blocked != null),
            onPause = { pauseFor(context, app) },
            onProceed = proceed,
            onDismiss = { guide = null },
            // only for an app stopped before: Google sometimes comes round to an app, so going ahead is worth a go
            onTryWithout = if (blocked == null) proceed else null,
        )
    }
    return gate
}

// to Play Protect's switch, remembering what it's for so Krate asks for it back on once the app is in
private fun pauseFor(
    context: Context,
    app: AppProfile,
) {
    PlayProtect.notePausedFor(context, app.displayName, app.packageName)
    PlayProtectReminderWorker.schedule(context)
    PlayProtect.open(context)
}

// the guide's words for an app: stopped just now, or stopped before and likely to be again. Honest about what Krate
// checked and what it can't vouch for
private fun appGuideText(
    app: AppProfile,
    action: String,
    stoppedNow: Boolean,
): PlayProtectGuideText {
    val name = app.displayName
    val signedBy =
        if (app.isTracked) "signed by the same developer as when you added it" else "signed by its developer"
    return PlayProtectGuideText(
        title = if (stoppedNow) "Play Protect stopped $name" else "Play Protect may stop $name",
        lead =
            if (stoppedNow) {
                "Pause it for a minute, ${action.lowercase()}, then turn it back on."
            } else {
                "It stopped this app before. Pause it for a minute, ${action.lowercase()}, then turn it back on."
            },
        steps =
            listOf(
                "Pause Play Protect" to PAUSE_STEP_DETAIL,
                "$action $name" to null,
                "Turn Play Protect back on" to "Krate reminds you straight after",
            ),
        explainer =
            "Play Protect warns about many apps from outside the Play Store, often because Google doesn't know " +
                "them yet rather than because they're harmful. Krate checked that this is $name's own build, " +
                "$signedBy, but it can't vouch for what an app does. If you're not sure about $name, don't install it.",
        proceedLabel = "$action now",
    )
}
