package dev.cl0ud9.krate.ui.details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.WalkStep
import dev.cl0ud9.krate.platform.backup.AutoBackupState
import dev.cl0ud9.krate.platform.backup.AutoLinks
import dev.cl0ud9.krate.platform.backup.BackupMode
import dev.cl0ud9.krate.platform.setup.WalkthroughMarks
import dev.cl0ud9.krate.ui.components.fadeThrough

// a setup that happens inside an app, one short screen at a time: what to do, a button to copy the text it needs or
// open the page it needs, and Back and Next that stay in view. It remembers where someone got to, so closing it and
// coming back picks up from the same step
@Composable
internal fun WalkthroughSheet(
    app: AppProfile,
    steps: List<WalkStep>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var index by rememberSaveable { mutableIntStateOf(WalkthroughMarks.step(context, app.packageName, steps.size)) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val last = index >= steps.lastIndex
    val progress by animateFloatAsState((index + 1f) / steps.size, label = "walkthrough-progress")
    SetupSheet(
        title = "Step by step",
        onDismiss = onDismiss,
        actions = {
            if (index > 0) {
                TextButton(onClick = { index -= 1 }) { Text("Back") }
            }
            Button(onClick = {
                if (last) {
                    WalkthroughMarks.finish(context, app.packageName)
                    onDismiss()
                } else {
                    index += 1
                    WalkthroughMarks.moveTo(context, app.packageName, index)
                }
            }) { Text(if (last) "All done" else "Next") }
        },
    ) {
        Text(
            text = "Step ${index + 1} of ${steps.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        AnimatedContent(targetState = step, transitionSpec = fadeThrough(), label = "walkthrough-step") { shown ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = shown.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = shown.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StepButtons(app, shown)
            }
        }
    }
}

// the buttons a step carries, if any: open the app, open its page, copy its text
@Composable
private fun StepButtons(
    app: AppProfile,
    step: WalkStep,
) {
    val context = LocalContext.current
    val copy = step.copy
    val link = step.link
    if (copy == null && link == null && !step.openApp) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (step.openApp) OpenAppTonal(app)
        if (link != null) {
            if (step.inApp) {
                InAppLinkButton(app, step, link)
            } else {
                FilledTonalButton(
                    onClick = { startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse(link)), null) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(step.linkLabel ?: "Open it") }
            }
        }
        if (copy != null) {
            FilledTonalButton(onClick = { copyText(context, copy) }, modifier = Modifier.fillMaxWidth()) {
                Text(step.copyLabel ?: "Copy")
            }
        }
    }
}

// a link only the app itself understands. With the helper on, Krate opens it inside the app and taps the button the app
// asks for; without, it opens it and the person taps. An app too old to know the link is told so, not left guessing
@Composable
private fun InAppLinkButton(
    app: AppProfile,
    step: WalkStep,
    link: String,
) {
    val context = LocalContext.current
    val intent =
        remember(app.packageName, link) { Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(app.packageName) }
    val understood = remember(intent) { intent.resolveActivity(context.packageManager) != null }
    val helperOn = rememberAutoBackupOn()
    val state = autoBackupStateFor(app.packageName)?.takeIf { it.isStoreRun() }
    val confirm = step.confirm
    if (!understood) {
        Text(
            text = "This version of ${app.displayName} can't take that link. Update it, or use the copy below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    val label = step.linkLabel ?: "Open it"
    if (confirm != null && helperOn) {
        val running = state is AutoBackupState.Running
        FilledTonalButton(
            onClick = { AutoLinks.confirmLink(app.packageName, link, confirm) },
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (running) "Working on it..." else label) }
        linkRunText(state)?.let { PanelText(it) }
        if (running) StopButton()
    } else {
        FilledTonalButton(
            onClick = { startSafely(context, intent, null) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(label) }
        if (confirm != null) {
            Text(
                text =
                    "Tap $confirm in ${app.displayName} when it asks. Turn on Krate's helper (in Backups) and Krate " +
                        "taps it for you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun AutoBackupState.isStoreRun(): Boolean =
    when (this) {
        is AutoBackupState.Running -> mode == BackupMode.LINK
        is AutoBackupState.Done -> mode == BackupMode.LINK
        is AutoBackupState.Failed -> mode == BackupMode.LINK
        AutoBackupState.Idle -> false
    }

private fun linkRunText(state: AutoBackupState?): String? =
    when (state) {
        is AutoBackupState.Done -> "Done. The app took it."
        is AutoBackupState.Failed -> state.reason
        else -> null
    }

private fun copyText(
    context: Context,
    text: String,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Krate", text))
    // Android 13 and later confirm a copy on their own
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
}

// the line under the row on the setup card: where they are, or that it's done
@Composable
internal fun walkthroughSubtitle(
    app: AppProfile,
    steps: List<WalkStep>,
): String {
    val context = LocalContext.current
    val changes = WalkthroughMarks.changes.intValue
    return remember(changes) {
        val finished = WalkthroughMarks.finished(context, app.packageName)
        val at = WalkthroughMarks.step(context, app.packageName, steps.size)
        when {
            finished -> "All done. Tap to go through it again"
            at > 0 -> "Pick up at step ${at + 1} of ${steps.size}"
            else -> "${steps.size} short steps, Krate holds your hand"
        }
    }
}
