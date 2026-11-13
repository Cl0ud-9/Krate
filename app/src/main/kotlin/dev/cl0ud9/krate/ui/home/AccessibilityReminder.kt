package dev.cl0ud9.krate.ui.home

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.platform.appContainer
import dev.cl0ud9.krate.platform.backup.AutoBackupRunner
import dev.cl0ud9.krate.platform.packageinfo.PackageChanges
import dev.cl0ud9.krate.platform.setup.AccessibilityWatch
import dev.cl0ud9.krate.platform.setup.WatchedService
import dev.cl0ud9.krate.platform.setup.stepIntent
import dev.cl0ud9.krate.ui.components.ButtonRow
import dev.cl0ud9.krate.ui.components.FoldAway
import dev.cl0ud9.krate.ui.components.SectionHeader
import dev.cl0ud9.krate.ui.details.startSafely
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.theme.ShapeCache

// what Home asks about when it matters: Play Protect after Krate's own update, if it was paused for it, and an
// accessibility helper left on
@Composable
internal fun HomeReminders(gap: Dp) {
    PlayProtectReminder(gap = gap)
    AccessibilityReminder(gap = gap)
}

// Krate's helper, or a catalog app's accessibility service, is still switched on. Some banking and payment apps
// refuse to open while one is on, so this asks for it to be switched off once its job is done, and never again for
// one the person said to keep. Only services Krate knows about: someone's own assistive tools are never asked about
@Composable
internal fun AccessibilityReminder(
    modifier: Modifier = Modifier,
    // space it leaves below it when shown, folded away with it
    gap: Dp = 0.dp,
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer() }
    val apps = remember(container) { container.catalogRepository.observeApps() }
    val catalog by apps.collectAsStateWithLifecycle(emptyList())
    var checks by remember { mutableIntStateOf(0) }
    // re-read on every return to Krate, so coming back from Android's accessibility settings settles it
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    // and whenever the switch is flipped while Krate is open (the notification shade can do that)
    DisposableEffect(context) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    checks++
                }
            }
        val setting = Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        context.contentResolver.registerContentObserver(setting, false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val changes = PackageChanges.version.intValue
    val switchedOff by AutoBackupRunner.switchedOff.collectAsStateWithLifecycle()
    val left = remember(catalog, checks, changes, switchedOff) { AccessibilityWatch.leftOn(context, catalog) }
    // the last list shown, kept while the card folds away
    var shown by remember { mutableStateOf(left) }
    if (left.isNotEmpty() && left != shown) shown = left
    val onChanged: () -> Unit = { checks++ }
    if (gap > 0.dp) {
        FoldAway(visible = left.isNotEmpty(), gap = gap) { ReminderCard(modifier, shown, onChanged) }
    } else if (left.isNotEmpty()) {
        ReminderCard(modifier, shown, onChanged)
    }
}

@Composable
private fun ReminderCard(
    modifier: Modifier,
    services: List<WatchedService>,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth().glassRim(ShapeCache.rounded16),
        shape = ShapeCache.rounded16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(
                title = reminderTitle(services),
                icon = painterResource(R.drawable.ic_shield_rounded),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text =
                    "Some banking and payment apps, like SBI YONO, won't open while an accessibility service is " +
                        "switched on. Switch it off when you're done; switching it back on takes a moment.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            services.forEach { service ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = service.label,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    FilledTonalButton(onClick = { turnOff(context, service, onChanged) }) { Text("Turn off") }
                }
            }
            ButtonRow {
                TextButton(
                    onClick = {
                        AccessibilityWatch.keepOn(context, services)
                        onChanged()
                    },
                ) { Text(if (services.size == 1) "Keep it on" else "Keep them on") }
            }
        }
    }
}

private fun reminderTitle(services: List<WatchedService>) =
    if (services.size == 1) "${services.first().label} is still on" else "Accessibility is still on"

// Krate's own helper switches itself off here and now; another app's service can only be switched off in Android's own
// accessibility settings, so its page opens there
private fun turnOff(
    context: Context,
    service: WatchedService,
    onChanged: () -> Unit,
) {
    if (service.own) {
        AutoBackupRunner.turnOff()
        onChanged()
        return
    }
    val step = service.step ?: return
    stepIntent(service.packageName, step)?.let { startSafely(context, it, null) }
}
