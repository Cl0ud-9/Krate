package dev.cl0ud9.krate.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.AppGuide
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.SetupKind
import dev.cl0ud9.krate.domain.model.SetupStep
import dev.cl0ud9.krate.platform.setup.SetupMarks
import dev.cl0ud9.krate.platform.setup.StepState
import dev.cl0ud9.krate.platform.setup.appInfoIntent
import dev.cl0ud9.krate.platform.setup.restrictedSettingsApply
import dev.cl0ud9.krate.platform.setup.stepState
import dev.cl0ud9.krate.ui.components.SectionHeader
import dev.cl0ud9.krate.ui.theme.ShapeCache

private const val DIVIDER_ALPHA = 0.5f

// once an app is installed: the permissions it needs (each a tap away, ticked off as Android reports them), the
// settings worth a look, and how to keep them. Re-read whenever Krate comes back, so a switch turned on elsewhere
// shows straight away
@Composable
internal fun SetupCard(
    app: AppProfile,
    guide: AppGuide,
    restorePending: Boolean,
) {
    val context = LocalContext.current
    val marks = remember(context) { SetupMarks(context) }
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    // what Android reports for each step; the ones it won't say are ticked by hand (manual)
    val reported = remember(checks, guide) { guide.setup.map { stepState(context, app.packageName, it) } }
    val states =
        remember(checks, reported) {
            guide.setup.mapIndexed { index, step ->
                val manualDone = reported[index] == StepState.UNKNOWN && marks.isMarked(app.packageName, step.kind)
                if (manualDone) StepState.DONE else reported[index]
            }
        }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.rounded16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeader(
                title = "Set it up",
                icon = rememberVectorPainter(Icons.Filled.Tune),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            val restore = guide.restore
            if (restorePending && restore != null) RestoreReminder(app = app, restore = restore)
            if (guide.setup.isNotEmpty()) {
                SetupProgress(steps = guide.setup, states = states)
                guide.setup.forEachIndexed { index, step ->
                    SetupStepRow(
                        app = app,
                        row = StepRow(index + 1, step, states[index], manual = reported[index] == StepState.UNKNOWN),
                        onMark = { done ->
                            marks.mark(app.packageName, step.kind, done)
                            checks++
                        },
                    )
                }
                // once, under the steps, while one Android may hold back is still to do
                val restricted = remember(checks) { restrictedSettingsApply(context, app.packageName) }
                if (restricted && restrictedStepLeft(guide, states)) RestrictedHelp(app = app)
            }
            if (guide.tips.isNotEmpty()) {
                SetupDivider()
                SettingsTips(app = app, guide = guide)
            }
            val backup = guide.backup
            if (backup != null) {
                SetupDivider()
                BackupSteps(backup = backup, restore = guide.restore)
            }
        }
    }
}

// "2 of 3 done", counting only what the app needs to work; optional steps don't hold it back
@Composable
private fun SetupProgress(
    steps: List<SetupStep>,
    states: List<StepState>,
) {
    val required = steps.indices.filter { !steps[it].optional }
    val done = required.count { states[it] == StepState.DONE }
    val text =
        when {
            required.isEmpty() -> "Nothing it can't work without. These are extras."
            done == required.size -> "All set. Everything it needs is switched on."
            else -> "$done of ${required.size} done. It needs these to work properly."
        }
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SetupStepRow(
    app: AppProfile,
    row: StepRow,
    onMark: (Boolean) -> Unit,
) {
    val step = row.step
    val state = row.state
    val context = LocalContext.current
    val done = state == StepState.DONE
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        StepBadge(number = row.number, done = done)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (step.optional) "${step.title} (optional)" else step.title,
                style = MaterialTheme.typography.titleSmall,
            )
            if (step.detail.isNotBlank()) {
                Text(
                    text = step.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!done) {
                FilledTonalButton(onClick = { openStep(context, app, step) }) { Text(actionLabel(step.kind)) }
                if (state == StepState.UNKNOWN) {
                    TextButton(onClick = { onMark(true) }) { Text("I've done this") }
                }
            } else if (row.manual) {
                TextButton(onClick = { onMark(false) }) { Text("Not done yet") }
            }
        }
    }
}

// one step as shown: its number, what Android reports, and whether it's ticked by hand
private data class StepRow(
    val number: Int,
    val step: SetupStep,
    val state: StepState,
    val manual: Boolean,
)

private fun restrictedStepLeft(
    guide: AppGuide,
    states: List<StepState>,
): Boolean = guide.setup.indices.any { guide.setup[it].kind in RESTRICTED_KINDS && states[it] != StepState.DONE }

// Android holds these back for apps from outside an app store until "Allow restricted settings" is turned on
private val RESTRICTED_KINDS = setOf(SetupKind.ACCESSIBILITY, SetupKind.USAGE_ACCESS, SetupKind.NOTIFICATION_ACCESS)

@Composable
private fun RestrictedHelp(app: AppProfile) {
    val context = LocalContext.current
    Surface(shape = ShapeCache.rounded12, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = "Switch greyed out?", style = MaterialTheme.typography.labelLarge)
            Text(
                text =
                    "Android holds this back for apps installed from outside an app store. Try the switch once, " +
                        "then open App info, tap ⋮ at the top right, choose Allow restricted settings, and come " +
                        "back to turn it on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { startSafely(context, appInfoIntent(app.packageName), null) }) {
                Text("Open App info")
            }
        }
    }
}

@Composable
private fun StepBadge(
    number: Int,
    done: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                painterResource(R.drawable.ic_check_circle_rounded),
                contentDescription = "Done",
                tint = colors.tertiary,
                modifier = Modifier.size(26.dp),
            )
        } else {
            Surface(shape = CircleShape, color = colors.secondaryContainer, modifier = Modifier.size(24.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$number",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTips(
    app: AppProfile,
    guide: AppGuide,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "Settings worth a look", style = MaterialTheme.typography.titleSmall)
        guide.settingsWhere?.let { where ->
            Text(
                text = "In $where:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        guide.tips.forEach { tip ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "•", color = MaterialTheme.colorScheme.primary)
                Text(
                    text = tip,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        launchIntent(context, app)?.let { intent ->
            FilledTonalButton(
                onClick = { startSafely(context, intent, null) },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("Open ${app.displayName}")
            }
        }
    }
}

@Composable
private fun BackupSteps(
    backup: String,
    restore: String?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(
            rememberVectorPainter(Icons.Filled.SettingsBackupRestore),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "Keep your settings", style = MaterialTheme.typography.titleSmall)
            val text =
                buildString {
                    append("To save them: ").append(backup).append('.')
                    if (restore !=
                        null
                    ) {
                        append(" To bring them back after a fresh install: ").append(restore).append('.')
                    }
                    append(" Updates keep them on their own; this is for a reinstall from scratch.")
                }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// after a reinstall that erased the app's data: how to put the saved settings back
@Composable
private fun RestoreReminder(
    app: AppProfile,
    restore: String,
) {
    val context = LocalContext.current
    Surface(shape = ShapeCache.rounded12, color = MaterialTheme.colorScheme.primaryContainer) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Bring your settings back",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "This was a fresh install, so it starts empty. If you saved a backup: $restore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            launchIntent(context, app)?.let { intent ->
                FilledTonalButton(onClick = { startSafely(context, intent, null) }) { Text("Open ${app.displayName}") }
            }
        }
    }
}

@Composable
private fun SetupDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA))
}
