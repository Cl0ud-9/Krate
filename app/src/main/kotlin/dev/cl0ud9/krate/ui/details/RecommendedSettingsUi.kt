package dev.cl0ud9.krate.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.domain.model.AppGuide
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.RecommendedSettings
import dev.cl0ud9.krate.platform.backup.AutoBackupState
import dev.cl0ud9.krate.platform.backup.RecommendedChoice
import dev.cl0ud9.krate.platform.backup.RecommendedMarks
import dev.cl0ud9.krate.ui.components.ActionSheet
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

// how many of the picks the sheet lists before "Show all"
internal const val PREVIEW_PICKS = 3

// what the picks change, kept short; and Krate applying them over the app's own settings, with a copy to paste by hand
// for anyone who'd rather. The buttons stay in view whatever the font size
@Composable
internal fun RecommendedSheet(
    app: AppProfile,
    guide: AppGuide,
    recommended: RecommendedSettings,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val spec = guide.autoBackup
    val helperOn = rememberAutoBackupOn()
    val state = autoBackupStateFor(app.packageName)
    val choice = rememberRecommendedChoice(app, recommended)
    var restrictedHelp by rememberSaveable { mutableStateOf(false) }
    var byHand by rememberSaveable { mutableStateOf(spec == null) }
    val running = state is AutoBackupState.Running
    val actions: (@Composable () -> Unit)? =
        when {
            running -> {
                { StopButton() }
            }
            spec == null -> {
                { ByHandActions(app, recommended) }
            }
            !helperOn -> {
                { TurnOnActions(restrictedHelp) { restrictedHelp = !restrictedHelp } }
            }
            else -> {
                {
                    ApplyActions(
                        ApplyPlan(app, spec, recommended, choice, canUndo(context, app, choice, state)),
                        onDismiss,
                    )
                }
            }
        }
    ActionSheet(title = "Recommended settings", onDismiss = onDismiss, actions = actions) {
        Text(
            text =
                "Krate's picks for ${app.displayName}, added to your settings. Anything else you've changed stays as " +
                    "it is, and yours are saved first, so Undo can put them back.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PicksList(recommended.summary)
        if (spec != null) {
            Surface(shape = ShapeCache.rounded16, color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PanelText(if (helperOn) applyText(state, choice) else HELPER_OFF_TEXT)
                    if (!helperOn && restrictedHelp) RestrictedHelpText(context)
                }
            }
            TextButton(onClick = {
                byHand = !byHand
            }) { Text(if (byHand) "Hide the by-hand way" else "Prefer to paste them yourself?") }
        }
        if (byHand) {
            ByHandText(guide)
            // with the helper in the footer the copy would have nowhere to be; without one, the footer has it
            if (spec != null) ByHandButtons(app, recommended)
        }
    }
}

private const val HELPER_OFF_TEXT =
    "Krate opens the app's Import / Export screen and applies them for you, the same way it makes backups. It needs " +
        "Android's accessibility access, and only uses it while it runs."

// the first few picks, the rest a tap away
@Composable
private fun PicksList(summary: List<String>) {
    var all by rememberSaveable { mutableStateOf(false) }
    val shown = if (all) summary else summary.take(PREVIEW_PICKS)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "\u2022", color = MaterialTheme.colorScheme.primary)
                Text(text = line, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (summary.size > PREVIEW_PICKS) {
            TextButton(onClick = { all = !all }) { Text(if (all) "Show fewer" else "Show all ${summary.size}") }
        }
    }
}
