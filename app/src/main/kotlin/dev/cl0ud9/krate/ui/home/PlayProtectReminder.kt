package dev.cl0ud9.krate.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.platform.selfupdate.PlayProtect
import dev.cl0ud9.krate.ui.components.ButtonRow
import dev.cl0ud9.krate.ui.components.SectionHeader
import dev.cl0ud9.krate.ui.theme.ShapeCache

// Play Protect was paused to update Krate: once the update is in (or was given up on), ask for it back on until
// the user has done it or says they have
@Composable
internal fun PlayProtectReminder() {
    val context = LocalContext.current
    var checks by remember { mutableIntStateOf(0) }
    // re-read on every return to Krate, so coming back from Play Protect's screen settles it
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val due = remember(checks) { PlayProtect.reminderDue(context) }
    if (!due) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.rounded16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(
                title = "Turn Play Protect back on",
                icon = painterResource(R.drawable.ic_shield_rounded),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text =
                    "You paused Play Protect to update Krate. Switching Scan apps with Play Protect back on keeps " +
                        "it checking the apps you install.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ButtonRow {
                TextButton(
                    onClick = {
                        PlayProtect.clearReminder(context)
                        checks++
                    },
                ) { Text("It's back on") }
                FilledTonalButton(
                    onClick = {
                        PlayProtect.clearReminder(context)
                        PlayProtect.open(context)
                    },
                ) { Text("Open Play Protect") }
            }
        }
    }
}
