package dev.cl0ud9.krate.ui.details

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.cl0ud9.krate.domain.links.declaredHosts
import dev.cl0ud9.krate.domain.links.wantedHosts
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.isTracked
import dev.cl0ud9.krate.platform.backup.AutoBackupRunner
import dev.cl0ud9.krate.platform.backup.AutoBackupState
import dev.cl0ud9.krate.platform.backup.AutoLinks
import dev.cl0ud9.krate.platform.links.OpenByDefault
import dev.cl0ud9.krate.platform.setup.appInfoIntent
import dev.cl0ud9.krate.ui.components.ActionSheet

@Composable
internal fun rememberLinkStanding(app: AppProfile): LinkStanding? {
    val links = app.openLinks
    if (links == null && !app.isTracked) return null
    val context = LocalContext.current
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val run = autoBackupStateFor(app.packageName)
    return remember(checks, run, app.packageName) {
        val states = OpenByDefault.states(context, app.packageName).orEmpty()
        val declared = states.mapTo(mutableSetOf()) { it.host.lowercase() }
        val wanted =
            if (links != null) {
                val installed = links.unlessInstalled.values.filterTo(mutableSetOf()) { isInstalled(context, it) }
                wantedHosts(links, installed, declared)
            } else {
                declaredHosts(states.map { it.host }, { OpenByDefault.takenByAnother(context, it, app.packageName) })
            }
        LinkStanding(wanted, states)
    }
}

private fun isInstalled(
    context: Context,
    packageName: String,
): Boolean = runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

// the row on the setup card; nothing when the app has no links to open, or there's nothing for this phone to switch
@Composable
internal fun OpenLinksRow(app: AppProfile) {
    val standing = rememberLinkStanding(app) ?: return
    var open by rememberSaveable { mutableStateOf(false) }
    if (standing.wanted.isEmpty() && OpenByDefault.supported) return
    SheetRow(
        icon = rememberVectorPainter(Icons.Filled.Link),
        title = "Open its links here",
        subtitle = linkSubtitle(app, standing),
        onClick = { open = true },
        highlight = OpenByDefault.supported && standing.todo.isNotEmpty(),
    )
    if (open) OpenLinksSheet(app = app, standing = standing, onDismiss = { open = false })
}

private fun linkSubtitle(
    app: AppProfile,
    standing: LinkStanding,
): String =
    when {
        !OpenByDefault.supported -> "Set in Android's settings for ${app.displayName}"
        standing.todo.isEmpty() -> "Done, they open in ${app.displayName}"
        standing.todo.size == standing.wanted.size ->
            "${linkCount(standing.wanted.size)} could open here instead of your browser"
        else -> "${standing.wanted.size - standing.todo.size} of ${standing.wanted.size} open here"
    }

// what the switch does, a way to do it for the person, and the way back
@Composable
internal fun OpenLinksSheet(
    app: AppProfile,
    standing: LinkStanding,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val helperOn = rememberAutoBackupOn()
    // a result from an earlier visit says nothing about how the links stand now
    LaunchedEffect(Unit) { AutoBackupRunner.acknowledge() }
    val state = autoBackupStateFor(app.packageName)?.takeIf { it.isOpenLinksRun() }
    val running = state is AutoBackupState.Running
    ActionSheet(
        title = "Open its links here",
        onDismiss = onDismiss,
        actions = {
            FilledTonalButton(onClick = { startSafely(context, openPage(app), appInfoIntent(app.packageName)) }) {
                Text("Open the page")
            }
            if (running) StopButton()
            if (canDoIt(helperOn, standing, running)) {
                Button(
                    onClick = { AutoLinks.openLinks(app.packageName, standing.todo) },
                ) { Text("Do it for me") }
            }
        },
    ) {
        Text(text = introText(app), style = MaterialTheme.typography.bodyMedium)
        standing.wanted.forEach { host -> LinkLine(host, on = host !in standing.todo) }
        statusText(state, helperOn, standing)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text =
                "To switch back, open the same page and untick them, or pick another app for them there. Nothing " +
                    "else on your phone changes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun canDoIt(
    helperOn: Boolean,
    standing: LinkStanding,
    running: Boolean,
): Boolean = helperOn && OpenByDefault.supported && !running && standing.todo.isNotEmpty()

private fun linkCount(count: Int): String = if (count == 1) "1 link" else "$count links"
