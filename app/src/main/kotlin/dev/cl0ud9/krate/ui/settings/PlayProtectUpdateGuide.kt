package dev.cl0ud9.krate.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.platform.selfupdate.PlayProtect

// why Play Protect objects to Krate, plainly, and that staying on this version is a fine answer too
internal const val PLAY_PROTECT_EXPLAINER =
    "Play Protect flags Krate because Krate installs apps from outside the Play Store. Krate checks every app it " +
        "installs against a signed list, and its code is public on GitHub. If you'd rather not continue, you can " +
        "keep using this version."

private val STEP_BADGE = 24.dp

// Play Protect stops Krate's own updates, so before one (and again if one was stopped anyway) the way through:
// pause it, update, and turn it back on, which Krate reminds about once the update is in
@Composable
internal fun PlayProtectUpdateGuide(
    blocked: Boolean,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                painterResource(R.drawable.ic_shield_rounded),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = if (blocked) "Play Protect stopped the update" else "Play Protect may stop this update",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = PLAY_PROTECT_EXPLAINER,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GuideStep(
            number = 1,
            title = "Pause Play Protect",
            detail = "Tap the settings icon at the top right, then turn off Scan apps with Play Protect.",
        ) {
            FilledTonalButton(
                onClick = {
                    PlayProtect.notePaused(context)
                    PlayProtect.open(context)
                },
            ) { Text("Open Play Protect") }
        }
        GuideStep(number = 2, title = "Update Krate") {
            Button(onClick = onUpdate) { Text(if (blocked) "Try again" else "Update now") }
        }
        GuideStep(
            number = 3,
            title = "Turn Play Protect back on",
            detail = "Krate reminds you once the update is in.",
        )
    }
}

@Composable
private fun GuideStep(
    number: Int,
    title: String,
    detail: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.size(STEP_BADGE),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = number.toString(), style = MaterialTheme.typography.labelMedium)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            action?.invoke()
        }
    }
}
