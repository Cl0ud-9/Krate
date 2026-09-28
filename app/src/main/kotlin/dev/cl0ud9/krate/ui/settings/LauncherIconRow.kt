package dev.cl0ud9.krate.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.platform.LauncherIcon
import dev.cl0ud9.krate.ui.components.KrateDialog
import dev.cl0ud9.krate.ui.components.KrateDialogHeader
import dev.cl0ud9.krate.ui.theme.ShapeCache

// hides Krate's launcher icon; turning it on first explains how to get back in
@Composable
internal fun LauncherIconRow() {
    val context = LocalContext.current
    var hidden by remember { mutableStateOf(LauncherIcon.isHidden(context)) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    SettingSwitchRow(
        item =
            SettingItem(
                painterResource(R.drawable.ic_visibility_off_rounded),
                "Hide Krate's icon",
                if (hidden) {
                    "Hidden. Open Krate from its notifications, or Settings > Apps > Krate > Open."
                } else {
                    "Keep Krate off your home screen and app drawer. It keeps working in the background."
                },
            ),
        checked = hidden,
        shape = settingsGroupShape(0, 1),
        onCheckedChange = { hide ->
            if (hide) {
                confirming = true
            } else {
                LauncherIcon.setHidden(context, hidden = false)
                hidden = false
            }
        },
    )
    if (confirming) {
        HideIconDialog(
            onConfirm = {
                LauncherIcon.setHidden(context, hidden = true)
                hidden = true
                confirming = false
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun HideIconDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrateDialog(
        header =
            KrateDialogHeader(
                tag = "Hide icon",
                icon = painterResource(R.drawable.ic_visibility_off_rounded),
                title = "Krate goes undercover",
                body =
                    "The icon leaves your home screen and app drawer. Krate itself keeps working and still " +
                        "checks for updates. Here's how to open it while it's hidden:",
            ),
        onDismissRequest = onDismiss,
        detail = { HiddenIconSteps() },
        footer = {
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Keep it") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onConfirm, shape = ShapeCache.rounded16) { Text("Hide icon") }
        },
    )
}

// the ways back in, spelled out step by step
@Composable
private fun HiddenIconSteps() {
    val notificationsOn = NotificationManagerCompat.from(LocalContext.current).areNotificationsEnabled()
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HiddenIconStep(
            number = 1,
            text =
                if (notificationsOn) {
                    "Tap any Krate notification, like an update alert."
                } else {
                    "Krate's notifications are off on this phone, so use the next way."
                },
        )
        HiddenIconStep(
            number = 2,
            text =
                "Open your phone's Settings > Apps > Krate, then tap Open. On some phones it's an arrow icon " +
                    "at the top of that page.",
        )
        HiddenIconStep(number = 3, text = "To bring the icon back, open Krate and turn this setting off in Appearance.")
    }
}

@Composable
private fun HiddenIconStep(
    number: Int,
    text: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp).wrapContentSize(),
            )
        }
        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
