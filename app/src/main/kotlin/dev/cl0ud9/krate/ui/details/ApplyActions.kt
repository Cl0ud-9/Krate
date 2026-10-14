package dev.cl0ud9.krate.ui.details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.cl0ud9.krate.domain.model.AppGuide
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.RecommendedSettings
import dev.cl0ud9.krate.platform.backup.AutoBackupRunner
import dev.cl0ud9.krate.platform.backup.RecommendedChoice
import dev.cl0ud9.krate.platform.backup.RecommendedMarks
import dev.cl0ud9.krate.platform.setup.RESTRICTED_SETTINGS_HELP
import dev.cl0ud9.krate.platform.setup.appInfoIntent
import dev.cl0ud9.krate.ui.components.ButtonRow

@Composable
internal fun ApplyActions(
    plan: ApplyPlan,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = plan.app
    val spec = plan.spec
    val recommended = plan.recommended
    val choice = plan.choice
    val canUndo = plan.canUndo
    if (canUndo) {
        TextButton(onClick = {
            // back to the settings they had, so this set counts as turned down
            if (AutoBackupRunner.restore(context, app.packageName, spec)) {
                RecommendedMarks.keptOwn(context, app.packageName, recommended.revision)
            }
        }) { Text("Undo") }
    } else if (choice != RecommendedChoice.APPLIED && choice != RecommendedChoice.KEPT_OWN) {
        TextButton(onClick = {
            RecommendedMarks.keptOwn(context, app.packageName, recommended.revision)
            onDismiss()
        }) { Text("Keep mine") }
    }
    Button(onClick = { AutoBackupRunner.applyRecommended(app.packageName, spec, recommended) }) {
        Text(if (choice == RecommendedChoice.APPLIED) "Apply again" else "Apply")
    }
}

// the helper isn't on: what it needs, a way past Android holding the switch back, and the switch
@Composable
internal fun TurnOnActions(
    restrictedHelp: Boolean,
    onToggleHelp: () -> Unit,
) {
    val context = LocalContext.current
    TextButton(onClick = onToggleHelp) { Text(if (restrictedHelp) "Hide that" else "Switch greyed out?") }
    Button(onClick = { startSafely(context, AutoBackupRunner.settingsIntent(context), null) }) { Text("Turn on") }
}

@Composable
internal fun RestrictedHelpText(context: Context) {
    PanelText(RESTRICTED_SETTINGS_HELP)
    FilledTonalButton(onClick = { startSafely(context, appInfoIntent(context.packageName), null) }) {
        Text("Open Krate's App info")
    }
}

// for an app Krate can't drive: open it and paste
@Composable
internal fun ByHandActions(
    app: AppProfile,
    recommended: RecommendedSettings,
) {
    val context = LocalContext.current
    OpenAppTonal(app)
    Button(onClick = { copyPicks(context, app, recommended) }) { Text("Copy") }
}

// the same two buttons, for the by-hand way when the footer is busy with Krate's own: open the app, and copy the picks
@Composable
internal fun ByHandButtons(
    app: AppProfile,
    recommended: RecommendedSettings,
) {
    val context = LocalContext.current
    ButtonRow {
        OpenAppTonal(app)
        Button(onClick = { copyPicks(context, app, recommended) }) { Text("Copy the picks") }
    }
}

// the catch with doing it by hand, spelled out: pasted on their own, the picks replace what's in the box
@Composable
internal fun ByHandText(guide: AppGuide) {
    Text(
        text =
            "Copy them and paste them into the app's Import / Export box" +
                guide.settingsWhere?.let { " (in $it)" }.orEmpty() + ". Pasted on their own, they replace what's in " +
                "that box, so anything you've changed that isn't on the list goes back to its default.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun copyPicks(
    context: Context,
    app: AppProfile,
    recommended: RecommendedSettings,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("${app.displayName} recommended settings", recommended.text))
    // Android 13 and later confirm a copy on their own
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copied Krate's picks for ${app.displayName}", Toast.LENGTH_SHORT).show()
    }
}
