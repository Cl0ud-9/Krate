package dev.cl0ud9.krate.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.ui.components.KrateSheet
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.navigation.pressable
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.voice.KrateMemory
import kotlinx.coroutines.launch

// as tall as the round header buttons beside it
private val AddButtonHeight = 40.dp

// where adding an app has got to: choosing how, suggesting it, or tracking it (with the link so far)
private sealed interface AddStep {
    data object Choose : AddStep

    data object Suggest : AddStep

    data class Track(
        val link: String,
    ) : AddStep
}

// the Apps tab's "+": the two ways to get an app that isn't here, one tap from anywhere on the tab
@Composable
fun AddAppAction(onOpenApp: (String) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    // a word beside the plus, so it reads as "get an app that isn't here" rather than an unlabelled button
    Button(
        onClick = {
            adding = true
            KrateMemory.addHintDone = true
        },
        modifier = Modifier.heightIn(min = AddButtonHeight),
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
        colors =
            ButtonDefaults.buttonColors(
                // a tonal pill, so it reads as a button on the header's colour wash, quieter than Settings beside it
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Add app", style = MaterialTheme.typography.labelLarge)
    }
    if (adding) AddAppFlow(onOpenApp = onOpenApp, onDone = { adding = false })
}

// choose, then the chosen sheet; each one closes before the next opens
@Composable
internal fun AddAppFlow(
    onOpenApp: (String) -> Unit,
    onDone: () -> Unit,
) {
    var step by rememberSaveable(stateSaver = AddStepSaver) { mutableStateOf<AddStep>(AddStep.Choose) }
    when (val current = step) {
        AddStep.Choose -> AddAppChooser(onDismiss = onDone, onPick = { step = it })
        AddStep.Suggest -> SuggestAppSheet(onDismiss = onDone, onTrackInstead = { link -> step = AddStep.Track(link) })
        is AddStep.Track -> TrackAppSheet(onDismiss = onDone, onOpenApp = onOpenApp, initialLink = current.link)
    }
}

private val AddStepSaver =
    androidx.compose.runtime.saveable.Saver<AddStep, String>(
        save = { step ->
            when (step) {
                AddStep.Choose -> "choose"
                AddStep.Suggest -> "suggest"
                is AddStep.Track -> "track:${step.link}"
            }
        },
        restore = { saved ->
            when {
                saved == "suggest" -> AddStep.Suggest
                saved.startsWith("track:") -> AddStep.Track(saved.removePrefix("track:"))
                else -> AddStep.Choose
            }
        },
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAppChooser(
    onDismiss: () -> Unit,
    onPick: (AddStep) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // the sheet slides away first, so the next one rises on its own rather than over it
    val pick = { next: AddStep -> scope.launch { sheetState.hide() }.invokeOnCompletion { onPick(next) } }
    KrateSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Add an app", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Missing something? There are two ways to get it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AddOption(
                icon = painterResource(R.drawable.ic_campaign_rounded),
                tint = MaterialTheme.colorScheme.tertiaryContainer,
                onTint = MaterialTheme.colorScheme.onTertiaryContainer,
                title = "Suggest it for the Krate",
                body = "Ask for it to join, checked and kept up to date for everyone.",
                onClick = { pick(AddStep.Suggest) },
            )
            AddOption(
                icon = painterResource(R.drawable.ic_github),
                tint = MaterialTheme.colorScheme.secondaryContainer,
                onTint = MaterialTheme.colorScheme.onSecondaryContainer,
                title = "Track it yourself",
                body = "Follow its releases straight from GitHub, Codeberg or GitLab.",
                onClick = { pick(AddStep.Track("")) },
            )
        }
    }
}

// one way in: an icon, what it does in a few words, and the whole row to tap
@Suppress("LongParameterList")
@Composable
private fun AddOption(
    icon: Painter,
    tint: Color,
    onTint: Color,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().glassRim(ShapeCache.rounded20).pressable(ShapeCache.rounded20, onClick),
        shape = ShapeCache.rounded20,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = ShapeCache.rounded16, color = tint) {
                Icon(icon, contentDescription = null, tint = onTint, modifier = Modifier.padding(12.dp).size(24.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// a one-line nudge at the top of the list until the "+" has been found, or the nudge put away
@Composable
internal fun AddAppHint(
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().glassRim(ShapeCache.rounded16).pressable(ShapeCache.rounded16, onOpen),
        shape = ShapeCache.rounded16,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 4.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = "Missing an app? Suggest it, or track it yourself.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
            }
        }
    }
}
