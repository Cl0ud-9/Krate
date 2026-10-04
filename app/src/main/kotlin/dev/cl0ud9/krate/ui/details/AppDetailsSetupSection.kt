package dev.cl0ud9.krate.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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
import dev.cl0ud9.krate.ui.util.tappableRow

private const val DIVIDER_ALPHA = 0.5f
private val BADGE = 26.dp

// once an app is installed: what's still to switch on, each a tap away and ticked off as Android reports it, then
// what's done folded into a line, and the settings and backups a tap away in their own sheets. Re-read whenever
// Krate comes back, so a switch turned on elsewhere shows straight away
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
    val rows =
        remember(checks, reported) {
            guide.setup.mapIndexed { index, step ->
                val manualDone = reported[index] == StepState.UNKNOWN && marks.isMarked(app.packageName, step.kind)
                StepRow(
                    index + 1,
                    step,
                    if (manualDone) StepState.DONE else reported[index],
                    reported[index] == StepState.UNKNOWN,
                )
            }
        }
    val onMark: (SetupStep, Boolean) -> Unit = { step, done ->
        marks.mark(app.packageName, step.kind, done)
        checks++
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.rounded16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SetupHeader(rows)
            if (restorePending) RestoreReminder(app = app, guide = guide)
            StepList(app = app, rows = rows, onMark = onMark)
            // once, under the steps, while one Android may hold back is still to do
            val restricted = remember(checks) { restrictedSettingsApply(context, app.packageName) }
            if (restricted && rows.any { it.step.kind in RESTRICTED_KINDS && !it.done }) RestrictedHelp(app = app)
            MoreRows(app = app, guide = guide, divided = rows.isNotEmpty())
        }
    }
}

// the title, with how far along the steps it needs are
@Composable
private fun SetupHeader(rows: List<StepRow>) {
    val required = rows.filterNot { it.step.optional }
    val progress =
        when {
            rows.isEmpty() -> null
            required.isEmpty() -> "Optional"
            required.all { it.done } -> "All set"
            else -> "${required.count { it.done }} of ${required.size}"
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(
            title = "Set it up",
            icon = rememberVectorPainter(Icons.Filled.Tune),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
        )
        if (progress != null) {
            Surface(shape = ShapeCache.rounded12, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = progress,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

// what's left, one compact row each, then what's done as a single line that opens up
@Composable
private fun StepList(
    app: AppProfile,
    rows: List<StepRow>,
    onMark: (SetupStep, Boolean) -> Unit,
) {
    val pending = rows.filterNot { it.done }
    val done = rows.filter { it.done }
    var showDone by rememberSaveable { mutableStateOf(false) }
    pending.forEach { row -> PendingStepRow(app = app, row = row, onMark = onMark) }
    if (pending.any { it.manual }) {
        Text(
            text = "Krate can't check some steps. Tap the circle once you've done one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (done.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth().tappableRow { showDone = !showDone },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DoneBadge()
            Text(
                text = if (pending.isEmpty()) "Everything's switched on" else "${done.size} done",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (showDone) "Hide" else "Show",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        AnimatedVisibility(visible = showDone) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 38.dp)) {
                done.forEach { row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = row.step.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (row.manual) TextButton(onClick = { onMark(row.step, false) }) { Text("Undo") }
                    }
                }
            }
        }
    }
}

// a step still to do: its number (a tick box when Krate can't check it), what and why, and the button for it
@Composable
private fun PendingStepRow(
    app: AppProfile,
    row: StepRow,
    onMark: (SetupStep, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val step = row.step
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val badge = Modifier.size(BADGE)
        val colors = MaterialTheme.colorScheme
        Surface(
            modifier =
                if (row.manual) {
                    badge
                        .clip(
                            CircleShape,
                        ).clickable(role = Role.Checkbox) { onMark(step, true) }
                } else {
                    badge
                },
            shape = CircleShape,
            color = if (row.manual) colors.surfaceContainerHighest else colors.secondaryContainer,
            border =
                if (row.manual) {
                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                } else {
                    null
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "${row.number}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = step.title, style = MaterialTheme.typography.titleSmall)
            val parts = listOfNotNull("Optional".takeIf { step.optional }, step.detail)
            val line = parts.filter { it.isNotBlank() }.joinToString(". ")
            if (line.isNotBlank()) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FilledTonalButton(
            onClick = { openStep(context, app, step) },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(actionLabel(step.kind))
        }
    }
}

@Composable
private fun DoneBadge() {
    Icon(
        painterResource(R.drawable.ic_check_circle_rounded),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.size(BADGE),
    )
}

// the settings worth knowing and how to keep them, each a row that opens its own sheet
@Composable
private fun MoreRows(
    app: AppProfile,
    guide: AppGuide,
    divided: Boolean,
) {
    var tips by rememberSaveable { mutableStateOf(false) }
    var keep by rememberSaveable { mutableStateOf(false) }
    val backup = guide.backup
    if (guide.tips.isEmpty() && backup == null) return
    if (divided) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA))
    Column {
        if (guide.tips.isNotEmpty()) {
            SheetRow(
                icon = rememberVectorPainter(Icons.Filled.Lightbulb),
                title = "Settings worth a look",
                subtitle = guide.settingsWhere?.let { "In $it" } ?: "${guide.tips.size} tips",
                onClick = { tips = true },
            )
        }
        if (backup != null) {
            SheetRow(
                icon = rememberVectorPainter(Icons.Filled.SettingsBackupRestore),
                title = "Keep your settings",
                subtitle = keepSubtitle(app, guide),
                onClick = { keep = true },
            )
        }
    }
    if (tips) TipsSheet(app = app, guide = guide, onDismiss = { tips = false })
    if (keep && backup != null) KeepSettingsSheet(app = app, guide = guide, onDismiss = { keep = false })
}

@Composable
private fun SheetRow(
    icon: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().tappableRow(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(BADGE),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right_rounded),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// one step as shown: its number, what Android reports, and whether it's ticked by hand
private data class StepRow(
    val number: Int,
    val step: SetupStep,
    val state: StepState,
    val manual: Boolean,
) {
    val done: Boolean
        get() = state == StepState.DONE
}

// Android holds these back for apps from outside an app store until "Allow restricted settings" is turned on
private val RESTRICTED_KINDS = setOf(SetupKind.ACCESSIBILITY, SetupKind.USAGE_ACCESS, SetupKind.NOTIFICATION_ACCESS)

@Composable
internal fun RestrictedHelp(
    app: AppProfile,
    packageName: String = app.packageName,
) {
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
            TextButton(onClick = { startSafely(context, appInfoIntent(packageName), null) }) {
                Text("Open App info")
            }
        }
    }
}
