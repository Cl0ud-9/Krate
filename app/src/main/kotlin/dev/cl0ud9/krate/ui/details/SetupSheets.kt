package dev.cl0ud9.krate.ui.details

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.domain.model.AppGuide
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.platform.backup.AppBackups
import dev.cl0ud9.krate.ui.theme.ShapeCache

// the settings worth knowing about, where they are, and a way into the app to change them
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TipsSheet(
    app: AppProfile,
    guide: AppGuide,
    onDismiss: () -> Unit,
) {
    SetupSheet(title = "Settings worth a look", onDismiss = onDismiss) {
        guide.settingsWhere?.let { where ->
            Text(
                text = "In $where:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        guide.tips.forEach { tip ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "•", color = MaterialTheme.colorScheme.primary)
                Text(text = tip, style = MaterialTheme.typography.bodyMedium)
            }
        }
        OpenAppTonal(app)
    }
}

// how to save the app's settings before a reinstall from scratch and put them back after, done by Krate when it can
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeepSettingsSheet(
    app: AppProfile,
    guide: AppGuide,
    onDismiss: () -> Unit,
) {
    SetupSheet(title = "Keep your settings", onDismiss = onDismiss) {
        Text(
            text = "Updates keep them on their own. A reinstall from scratch starts the app empty, so save them first.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        guide.autoBackup?.let { spec -> AutoBackupPanel(app = app, spec = spec) }
        Surface(shape = ShapeCache.rounded16, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (guide.autoBackup != null) "Or by hand" else "How",
                    style = MaterialTheme.typography.titleSmall,
                )
                guide.backup?.let { HandStep(label = "To save them", text = it) }
                guide.restore?.let { HandStep(label = "To bring them back", text = it) }
                OpenAppTonal(app)
            }
        }
    }
}

@Composable
private fun HandStep(
    label: String,
    text: String,
) {
    Text(
        text = "$label: $text.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OpenAppTonal(app: AppProfile) {
    val context = LocalContext.current
    launchIntent(context, app)?.let { intent ->
        FilledTonalButton(onClick = { startSafely(context, intent, null) }) { Text("Open ${app.displayName}") }
    }
}

// the line under "Keep your settings" on the card: when it was last saved, or what's on offer
@Composable
internal fun keepSubtitle(
    app: AppProfile,
    guide: AppGuide,
): String {
    val saved = AppBackups.read(LocalContext.current, app.packageName)
    return when {
        saved != null -> "Backed up ${relativeTime(saved.savedAtMillis)}"
        guide.autoBackup != null -> "Krate can back them up for you"
        else -> "Before a reinstall from scratch"
    }
}

internal fun relativeTime(millis: Long): String {
    val now = System.currentTimeMillis()
    if (now - millis < DateUtils.MINUTE_IN_MILLIS) return "just now"
    return DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS).toString().lowercase()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
