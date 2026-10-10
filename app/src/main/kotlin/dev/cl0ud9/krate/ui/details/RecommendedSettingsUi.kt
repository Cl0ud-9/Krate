package dev.cl0ud9.krate.ui.details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.domain.model.AppGuide
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.AutoBackup
import dev.cl0ud9.krate.domain.model.RecommendedSettings
import dev.cl0ud9.krate.platform.backup.AutoBackupRunner
import dev.cl0ud9.krate.platform.backup.AutoBackupState
import dev.cl0ud9.krate.platform.backup.RecommendedChoice
import dev.cl0ud9.krate.platform.backup.RecommendedMarks
import dev.cl0ud9.krate.ui.components.ButtonRow
import dev.cl0ud9.krate.ui.theme.ShapeCache

// what's been decided about this app's picks, re-read whenever that changes
@Composable
internal fun rememberRecommendedChoice(
    app: AppProfile,
    recommended: RecommendedSettings,
): RecommendedChoice {
    val context = LocalContext.current
    val changes = RecommendedMarks.changes.intValue
    return remember(changes, recommended.revision) {
        RecommendedMarks.choice(context, app.packageName, recommended.revision)
    }
}

// on the setup card while nothing's been decided: the picks are offered once, or again when they change. Turning them
// down is remembered for this set, so it doesn't come back until the catalog's picks do
@Composable
internal fun RecommendedOffer(
    app: AppProfile,
    recommended: RecommendedSettings,
    onSee: () -> Unit,
) {
    val context = LocalContext.current
    val choice = rememberRecommendedChoice(app, recommended)
    if (choice != RecommendedChoice.UNDECIDED && choice != RecommendedChoice.OUTDATED) return
    Surface(shape = ShapeCache.rounded16, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text =
                    if (choice == RecommendedChoice.OUTDATED) {
                        "Krate's recommended settings have changed"
                    } else {
                        "Start with Krate's recommended settings?"
                    },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            PanelText(offerPreview(recommended.summary))
            ButtonRow {
                TextButton(onClick = { RecommendedMarks.keptOwn(context, app.packageName, recommended.revision) }) {
                    Text("Keep mine")
                }
                Button(onClick = onSee) { Text("See them") }
            }
        }
    }
}

// the first pick, and how many more
private fun offerPreview(summary: List<String>): String {
    val first = summary.first().trimEnd('.')
    val more = summary.size - 1
    return if (more > 0) "$first, and $more more." else "$first."
}

// the line under "Recommended settings" on the card
@Composable
internal fun recommendedSubtitle(
    app: AppProfile,
    recommended: RecommendedSettings,
): String {
    val context = LocalContext.current
    return when (rememberRecommendedChoice(app, recommended)) {
        RecommendedChoice.APPLIED ->
            RecommendedMarks.appliedAt(context, app.packageName)?.let { "Applied ${relativeTime(it)}" } ?: "Applied"
        RecommendedChoice.OUTDATED -> "Krate's picks have changed since you applied them"
        RecommendedChoice.KEPT_OWN -> "You're keeping your own"
        RecommendedChoice.UNDECIDED -> "${recommended.summary.size} picks from Krate"
    }
}

// what the picks change, and Krate applying them over the app's own settings, or a copy to paste by hand
@Composable
internal fun RecommendedSheet(
    app: AppProfile,
    guide: AppGuide,
    recommended: RecommendedSettings,
    onDismiss: () -> Unit,
) {
    SetupSheet(title = "Recommended settings", onDismiss = onDismiss) {
        Text(
            text =
                "Krate's picks for ${app.displayName}. Applying them adds these to the settings you have now: " +
                    "anything else you've changed stays as it is, and your current settings are saved first, so " +
                    "you can put them back.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        recommended.summary.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "•", color = MaterialTheme.colorScheme.primary)
                Text(text = line, style = MaterialTheme.typography.bodyMedium)
            }
        }
        guide.autoBackup?.let { spec ->
            ApplyPanel(app = app, spec = spec, recommended = recommended, onKeepOwn = onDismiss)
        }
        ByHandPanel(app = app, guide = guide, recommended = recommended)
    }
}

// Krate applying them itself, through the same helper as automatic backups
@Composable
private fun ApplyPanel(
    app: AppProfile,
    spec: AutoBackup,
    recommended: RecommendedSettings,
    onKeepOwn: () -> Unit,
) {
    val context = LocalContext.current
    val on = rememberAutoBackupOn()
    val state = autoBackupStateFor(app.packageName)
    val choice = rememberRecommendedChoice(app, recommended)
    Surface(shape = ShapeCache.rounded16, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Apply them for you",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (!on) {
                TurnOnAutoBackup(
                    "Krate opens the app's Import / Export screen and applies them for you, the same way it makes " +
                        "backups. It needs Android's accessibility access, and only uses it while it runs.",
                )
                return@Column
            }
            PanelText(applyText(state, choice))
            if (state is AutoBackupState.Running) return@Column
            ButtonRow {
                if (canUndo(context, app, choice, state)) {
                    TextButton(onClick = {
                        // back to the settings they had, so this set counts as turned down
                        if (AutoBackupRunner.restore(context, app.packageName, spec)) {
                            RecommendedMarks.keptOwn(context, app.packageName, recommended.revision)
                        }
                    }) { Text("Undo") }
                } else if (choice != RecommendedChoice.APPLIED && choice != RecommendedChoice.KEPT_OWN) {
                    TextButton(onClick = {
                        RecommendedMarks.keptOwn(context, app.packageName, recommended.revision)
                        onKeepOwn()
                    }) { Text("Keep mine") }
                }
                Button(onClick = { AutoBackupRunner.applyRecommended(app.packageName, spec, recommended) }) {
                    Text(if (choice == RecommendedChoice.APPLIED) "Apply again" else "Apply")
                }
            }
        }
    }
}

// the picks to paste in the app's own Import / Export box, with the catch spelled out: pasted on their own, they
// replace what's there
@Composable
private fun ByHandPanel(
    app: AppProfile,
    guide: AppGuide,
    recommended: RecommendedSettings,
) {
    val context = LocalContext.current
    Surface(shape = ShapeCache.rounded16, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (guide.autoBackup != null) "Or by hand" else "How",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text =
                    "Copy them and paste them into the app's Import / Export box" +
                        guide.settingsWhere?.let { " (in $it)" }.orEmpty() + ". Pasted on their own, they replace " +
                        "what's in that box, so anything you've changed that isn't on the list goes back to its " +
                        "default.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ButtonRow {
                OpenAppTonal(app)
                FilledTonalButton(onClick = { copyPicks(context, app, recommended) }) { Text("Copy") }
            }
        }
    }
}

private fun copyPicks(
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
