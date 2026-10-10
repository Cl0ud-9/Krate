package dev.cl0ud9.krate.ui.components

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R

private val STEP_BADGE = 22.dp

// where to find the switch, said the same way wherever the guide appears
internal const val PAUSE_STEP_DETAIL = "Settings icon at the top right, then Scan apps with Play Protect"

// what one Play Protect guide says: its headline and line under it, the steps, why Play Protect objects, and the
// main button once Play Protect has been opened
internal class PlayProtectGuideText(
    val title: String,
    val lead: String,
    val steps: List<Pair<String, String?>>,
    val explainer: String,
    val proceedLabel: String,
)

// pause Play Protect, do the thing it stopped, turn it back on. One button walks the steps: first to Play Protect,
// then on to the install or update. [onTryWithout], when there is one, goes ahead without pausing, for an app Play
// Protect may have come round to since
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayProtectGuideSheet(
    text: PlayProtectGuideText,
    onPause: () -> Unit,
    onProceed: () -> Unit,
    onDismiss: () -> Unit,
    onTryWithout: (() -> Unit)? = null,
) {
    var opened by rememberSaveable { mutableStateOf(false) }
    var why by rememberSaveable { mutableStateOf(false) }
    KrateSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GuideSteps(text = text, paused = opened)
            AnimatedVisibility(visible = why) {
                Text(
                    text = text.explainer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ButtonRow {
                if (!why) TextButton(onClick = { why = true }) { Text("Why?") }
                if (opened) {
                    TextButton(onClick = onPause) { Text("Open it again") }
                } else if (onTryWithout != null) {
                    TextButton(onClick = {
                        onDismiss()
                        onTryWithout()
                    }) { Text("Try anyway") }
                }
                Button(onClick = {
                    if (opened) {
                        onDismiss()
                        onProceed()
                    } else {
                        onPause()
                        opened = true
                    }
                }) { Text(if (opened) text.proceedLabel else "Open Play Protect") }
            }
        }
    }
}

@Composable
private fun GuideSteps(
    text: PlayProtectGuideText,
    paused: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            painterResource(R.drawable.ic_shield_rounded),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Text(text = text.title, style = MaterialTheme.typography.titleLarge)
    }
    Text(
        text = text.lead,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    text.steps.forEachIndexed { index, (title, detail) ->
        GuideStep(number = index + 1, title = title, detail = detail, done = index == 0 && paused)
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
