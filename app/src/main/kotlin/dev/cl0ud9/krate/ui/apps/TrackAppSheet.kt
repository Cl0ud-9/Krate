package dev.cl0ud9.krate.ui.apps

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.domain.tracked.ReleaseAsset
import dev.cl0ud9.krate.domain.tracked.versionFromTag
import dev.cl0ud9.krate.platform.appContainer
import dev.cl0ud9.krate.ui.components.AppIconAvatar
import dev.cl0ud9.krate.ui.components.KrateLinearProgress
import dev.cl0ud9.krate.ui.components.KrateSheet
import dev.cl0ud9.krate.ui.components.downloadFraction
import dev.cl0ud9.krate.ui.components.fadeThrough
import dev.cl0ud9.krate.ui.components.withTabularFigures
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.steadyHeight
import dev.cl0ud9.krate.voice.KrateMemory
import dev.cl0ud9.krate.voice.KrateVoice
import dev.cl0ud9.krate.voice.Moment
import dev.cl0ud9.krate.voice.rememberKrateLine
import java.util.Locale

private const val BYTES_PER_MB = 1_048_576.0
private const val FINGERPRINT_SHOWN = 16
private const val FINGERPRINT_GROUP = 4

// adding an app to follow from GitHub, Codeberg or GitLab: paste its link, Krate looks at its releases, checks the app
// file, and shows what it found before anything is saved
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackAppSheet(
    onDismiss: () -> Unit,
    onOpenApp: (String) -> Unit,
    initialLink: String = "",
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer() }
    val scope = rememberCoroutineScope()
    val flow =
        remember(container, scope) {
            TrackAppFlow(
                scope,
                container.trackedAppInspector,
                container.trackedAppsRepository,
                container.catalogRepository,
                container.installedPackageReader,
                container.artifactDownloader,
            )
        }
    // a file checked but not tracked isn't left behind
    DisposableEffect(flow) { onDispose { flow.close() } }
    // the link typed so far, kept across every step, so a lookup that went wrong can be fixed rather than retyped
    val link = rememberTextFieldState(initialLink)
    val open = { id: String ->
        onDismiss()
        onOpenApp(id)
    }
    KrateSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = "Track an app", style = MaterialTheme.typography.headlineSmall)
            TrackSteps(flow = flow, link = link, open = open)
        }
    }
}

@Composable
private fun EnterLink(
    step: TrackStep.Enter,
    link: TextFieldState,
    onLookUp: (String) -> Unit,
    onOpenApp: (String) -> Unit,
) {
    Text(
        text =
            "Follow an app's releases on GitHub, Codeberg or GitLab. Krate checks it for updates like the apps in " +
                "the Krate, and only installs builds signed by the same developer as the first one.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        state = link,
        label = { Text("Link to its repository") },
        placeholder = { Text("github.com/owner/app") },
        lineLimits = TextFieldLineLimits.SingleLine,
        isError = step.problem != null,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go,
            ),
        onKeyboardAction = { onLookUp(link.text.toString()) },
        modifier = Modifier.fillMaxWidth().steadyHeight(),
    )
    // its own line under the box, so a long one wraps instead of being cut off by the box's steady height
    step.problem?.let { problem ->
        Text(
            text = problem,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    // laid out like Suggest an app's: the main action full width, anything else under it
    Button(
        onClick = { onLookUp(link.text.toString()) },
        enabled = link.text.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Look it up") }
    step.openAppId?.let { id ->
        FilledTonalButton(onClick = { onOpenApp(id) }, modifier = Modifier.fillMaxWidth()) { Text("Open it") }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Waiting(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LoadingIndicator()
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

// a release with several files this phone can run; the best fit comes first and is picked already
@Composable
private fun ChooseFile(
    step: TrackStep.Choose,
    onPick: (ReleaseAsset) -> Unit,
    onUse: () -> Unit,
    onBack: () -> Unit,
) {
    Text(
        text =
            "${step.found.ref.slug} ${versionFromTag(step.found.release.tag)} comes as a few files. Krate picked " +
                "the best fit for your phone; it looks for the same kind of file in every later release.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column {
        step.found.candidates.forEach { asset ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.RadioButton) { onPick(asset) }
                        .padding(vertical = 4.dp),
            ) {
                RadioButton(selected = asset == step.picked, onClick = { onPick(asset) })
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = asset.name, style = MaterialTheme.typography.bodyMedium)
                    if (asset.sizeBytes > 0) {
                        Text(
                            text = megabytes(asset.sizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    Button(onClick = onUse, modifier = Modifier.fillMaxWidth()) { Text("Check this one") }
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
}

@Composable
private fun Checking(step: TrackStep.Checking) {
    val total = step.totalBytes
    Text(text = rememberKrateLine(Moment.TRACK_CHECKING), style = MaterialTheme.typography.titleSmall)
    KrateLinearProgress(progress = downloadFraction(step.bytes, total))
    Text(
        text =
            if (total != null) {
                "${megabytes(step.bytes)} of ${megabytes(total)}. Krate reads who made it before anything is added."
            } else {
                "${megabytes(step.bytes)} so far. Krate reads who made it before anything is added."
            },
        style = MaterialTheme.typography.bodySmall.withTabularFigures(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// what Krate found, before anything is saved
@Composable
private fun ConfirmApp(
    step: TrackStep.Confirm,
    preview: dev.cl0ud9.krate.domain.model.AppProfile,
    onTrack: () -> Unit,
    onBack: () -> Unit,
) {
    // the very first app someone finds themselves gets a word of its own
    val firstFind = remember { KrateMemory.hasNoFinds }
    val found = rememberKrateLine(Moment.TRACK_FOUND, key = step.app.packageName)
    Text(
        text = if (firstFind && KrateVoice.playful) "Your first find." else found,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Surface(shape = ShapeCache.rounded20, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppIconAvatar(app = preview, size = 52.dp)
                Column {
                    Text(
                        text = step.app.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text =
                            listOfNotNull(
                                step.app.versionName ?: versionFromTag(step.found.release.tag),
                                step.app.packageName,
                            ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            step.found.description?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                text = signedByLine(step),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (step.signedDifferently) {
        Surface(shape = ShapeCache.rounded16, color = MaterialTheme.colorScheme.errorContainer) {
            Text(
                text =
                    "The ${step.app.label} on your phone was signed by someone else, so this one can't go over it. " +
                        "Installing it from here means reinstalling it from scratch, which clears its data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
    Button(onClick = onTrack, modifier = Modifier.fillMaxWidth()) { Text("Track it") }
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Not this one") }
}

private fun megabytes(bytes: Long): String = String.format(Locale.UK, "%.1f MB", bytes / BYTES_PER_MB)

// where it's from and who signed it, the start of their fingerprint shown in groups
private fun signedByLine(step: TrackStep.Confirm): String {
    val ref = step.found.ref
    val signer =
        step.app.certificateSha256
            .take(FINGERPRINT_SHOWN)
            .chunked(FINGERPRINT_GROUP)
            .joinToString(" ")
    return "From ${ref.slug} on ${ref.forge.siteName}. Signed by $signer..., and Krate only installs updates " +
        "signed the same way."
}

// the sheet's current step; one fades into the next while the sheet eases to its new height
@Composable
private fun TrackSteps(
    flow: TrackAppFlow,
    link: TextFieldState,
    open: (String) -> Unit,
) {
    AnimatedContent(
        targetState = flow.step,
        contentKey = { it::class },
        transitionSpec = fadeThrough(),
        contentAlignment = Alignment.TopStart,
        label = "track-step",
    ) { shown ->
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (val step = shown) {
                is TrackStep.Enter -> EnterLink(step, link, onLookUp = flow::lookUp, onOpenApp = open)
                TrackStep.LookingUp -> Waiting(rememberKrateLine(Moment.TRACK_LOOKING))
                is TrackStep.Choose ->
                    ChooseFile(
                        step,
                        onPick = flow::pick,
                        onUse = flow::usePicked,
                        onBack = flow::startOver,
                    )
                is TrackStep.Checking -> Checking(step)
                is TrackStep.Confirm ->
                    ConfirmApp(
                        step = step,
                        preview = flow.preview(step),
                        onTrack = { flow.confirm(onTracked = open) },
                        onBack = flow::startOver,
                    )
                TrackStep.Saving -> Waiting("Adding it to your apps...")
            }
        }
    }
}
