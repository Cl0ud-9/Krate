package dev.cl0ud9.krate.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.cl0ud9.krate.platform.selfupdate.PlayProtect
import dev.cl0ud9.krate.ui.components.PAUSE_STEP_DETAIL
import dev.cl0ud9.krate.ui.components.PlayProtectGuideSheet
import dev.cl0ud9.krate.ui.components.PlayProtectGuideText

// said before an update, so Google's own warning doesn't come as a surprise
internal const val PLAY_PROTECT_HEADS_UP = "Play Protect may stop this. If it does, Krate shows you the way past it."

// why Play Protect objects to Krate, plainly, and that staying on this version is a fine answer too
private val KRATE_GUIDE =
    PlayProtectGuideText(
        title = "Play Protect stopped the update",
        lead = "Pause it for a minute, update, then turn it back on.",
        steps =
            listOf(
                "Pause Play Protect" to PAUSE_STEP_DETAIL,
                "Update Krate" to null,
                "Turn Play Protect back on" to "Krate reminds you once the update is in",
            ),
        explainer =
            "Play Protect flags Krate because Krate installs apps from outside the Play Store. Krate checks every " +
                "app it installs against a signed list, and its code is public on GitHub. If you'd rather not " +
                "continue, you can keep using this version.",
        proceedLabel = "Update now",
    )

// once Play Protect has stopped Krate's own update: pause it, update, turn it back on, with a reminder for the last
@Composable
internal fun PlayProtectSheet(
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    PlayProtectGuideSheet(
        text = KRATE_GUIDE,
        onPause = {
            PlayProtect.notePaused(context)
            PlayProtect.open(context)
        },
        onProceed = onUpdate,
        onDismiss = onDismiss,
    )
}
