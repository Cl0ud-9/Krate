package dev.cl0ud9.krate.ui.details

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import dev.cl0ud9.krate.domain.model.AppGuide
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.RecommendedSettings
import dev.cl0ud9.krate.platform.backup.AutoBackupState
import dev.cl0ud9.krate.platform.backup.AutoLinks
import dev.cl0ud9.krate.platform.backup.RecommendedChoice
import dev.cl0ud9.krate.platform.links.OpenByDefault
import dev.cl0ud9.krate.ui.components.KrateDialog
import dev.cl0ud9.krate.ui.components.KrateDialogHeader

@Composable
private fun rememberInstallTime(packageName: String): Long {
    val context = LocalContext.current
    return remember(packageName) {
        runCatching { context.packageManager.getPackageInfo(packageName, 0).lastUpdateTime }.getOrDefault(0L)
    }
}

// what's offered right after the first install: the picks, then the links, one at a time
@Composable
internal fun InstallPrompts(
    app: AppProfile,
    guide: AppGuide,
    recommended: RecommendedSettings?,
    onOpenSheet: () -> Unit,
) {
    if (recommended != null) RecommendedInstallPrompt(app, guide, recommended, onOpenSheet)
    LinksInstallPrompt(app, afterPicks = recommended?.let { rememberRecommendedChoice(app, it) })
}

// shown once after the first install, when some of the app's links would open elsewhere; waits until the picks prompt
// (if there is one) has been dealt with and nothing else is running, so the two never stack
@Composable
internal fun LinksInstallPrompt(
    app: AppProfile,
    afterPicks: RecommendedChoice?,
) {
    val standing = rememberLinkStanding(app)
    val helperOn = rememberAutoBackupOn()
    // keyed to this install, so putting the app on again later (after removing it) offers the links afresh
    val installedAt = rememberInstallTime(app.packageName)
    var handled by rememberSaveable(app.id, installedAt) { mutableStateOf(false) }
    var sheet by rememberSaveable(app.id, installedAt) { mutableStateOf(false) }
    val busy = autoBackupStateFor(app.packageName) is AutoBackupState.Running
    val offer = app.openLinks != null && OpenByDefault.supported
    val waiting = !offer || handled || busy || afterPicks == RecommendedChoice.UNDECIDED
    if (standing == null || waiting || standing.todo.isEmpty()) return
    if (sheet) {
        OpenLinksSheet(app = app, standing = standing, onDismiss = { handled = true })
        return
    }
    KrateDialog(
        header =
            KrateDialogHeader(
                tag = "Fresh install",
                icon = rememberVectorPainter(Icons.Filled.Link),
                title = "Open its links here?",
                body =
                    "Links to ${standing.todo.joinToString(", ")} would open in your browser or another app. Krate " +
                        "can send them to ${app.displayName} instead. You can switch it back any time.",
            ),
        onDismissRequest = { handled = true },
        footer = {
            TextButton(onClick = { handled = true }) { Text("Not now") }
            Button(onClick = {
                if (helperOn) {
                    AutoLinks.openLinks(app.packageName, standing.toTick, standing.releases)
                    handled = true
                } else {
                    sheet = true
                }
            }) { Text("Set it up") }
        },
    )
}
