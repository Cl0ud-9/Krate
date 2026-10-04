package dev.cl0ud9.krate.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.platform.selfupdate.PlayProtect
import dev.cl0ud9.krate.ui.components.ButtonRow

// said before an update, so Google's own warning doesn't come as a surprise
internal const val PLAY_PROTECT_HEADS_UP = "Play Protect may stop this. If it does, Krate shows you the way past it."

// why Play Protect objects to Krate, plainly, and that staying on this version is a fine answer too
private const val PLAY_PROTECT_EXPLAINER =
    "Play Protect flags Krate because Krate installs apps from outside the Play Store. Krate checks every app it " +
        "installs against a signed list, and its code is public on GitHub. If you'd rather not continue, you can " +
        "keep using this version."

private val STEP_BADGE = 22.dp

// once Play Protect has stopped Krate's own update: pause it, update, turn it back on. One button walks the steps,
// and Krate reminds about turning it back on once the update is in
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayProtectSheet(
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var opened by rememberSaveable { mutableStateOf(false) }
    var why by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 24.dp,
                    ).padding(bottom = 16.dp)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SheetSteps(paused = opened)
            AnimatedVisibility(visible = why) {
                Text(
                    text = PLAY_PROTECT_EXPLAINER,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SheetButtons(
                opened = opened,
                why = why,
                onWhy = { why = true },
                onOpen = {
                    PlayProtect.notePaused(context)
                    PlayProtect.open(context)
                    opened = true
                },
                onUpdate = {
                    onDismiss()
                    onUpdate()
                },
            )
        }
    }
}

@Composable
private fun SheetSteps(paused: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            painterResource(R.drawable.ic_shield_rounded),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Text(text = "Play Protect stopped the update", style = MaterialTheme.typography.titleLarge)
    }
    Text(
        text = "Pause it for a minute, update, then turn it back on.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    STEPS.forEachIndexed { index, (title, detail) ->
        GuideStep(number = index + 1, title = title, detail = detail, done = index == 0 && paused)
    }
}

private val STEPS =
    listOf(
        "Pause Play Protect" to "Settings icon at the top right, then Scan apps with Play Protect",
        "Update Krate" to null,
        "Turn Play Protect back on" to "Krate reminds you once the update is in",
    )

// the main button moves on once Play Protect has been opened: first there, then the update itself
@Composable
private fun SheetButtons(
    opened: Boolean,
    why: Boolean,
    onWhy: () -> Unit,
    onOpen: () -> Unit,
    onUpdate: () -> Unit,
) {
    ButtonRow {
        if (!why) TextButton(onClick = onWhy) { Text("Why?") }
        if (opened) TextButton(onClick = onOpen) { Text("Open it again") }
        Button(onClick = if (opened) onUpdate else onOpen) {
            Text(if (opened) "Update now" else "Open Play Protect")
        }
    }
}

@Composable
private fun GuideStep(
    number: Int,
    title: String,
    detail: String? = null,
    done: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.padding(top = 1.dp).size(STEP_BADGE),
            shape = CircleShape,
            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
            contentColor =
                if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (done) {
                    Icon(
                        painterResource(R.drawable.ic_check_rounded),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Text(text = number.toString(), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
