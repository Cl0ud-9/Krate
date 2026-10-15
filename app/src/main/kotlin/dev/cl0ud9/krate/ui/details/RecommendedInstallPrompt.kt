package dev.cl0ud9.krate.ui.details

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import dev.cl0ud9.krate.domain.model.AppGuide
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.RecommendedSettings
import dev.cl0ud9.krate.platform.backup.AutoBackupRunner
import dev.cl0ud9.krate.platform.backup.RecommendedChoice
import dev.cl0ud9.krate.platform.backup.RecommendedMarks
import dev.cl0ud9.krate.ui.components.KrateDialog
import dev.cl0ud9.krate.ui.components.KrateDialogHeader

// shown once, right after the app goes on for the first time: the picks or their own, there and then. Applying runs
// straight away when the helper is on; otherwise it opens the sheet, which walks through switching it on
@Composable
internal fun RecommendedInstallPrompt(
    app: AppProfile,
    guide: AppGuide,
    recommended: RecommendedSettings,
    onOpenSheet: () -> Unit,
) {
    val context = LocalContext.current
    val choice = rememberRecommendedChoice(app, recommended)
    val helperOn = rememberAutoBackupOn()
    var handled by rememberSaveable(app.id, recommended.revision) { mutableStateOf(false) }
    if (handled || choice != RecommendedChoice.UNDECIDED) return
    val spec = guide.autoBackup
    KrateDialog(
        header =
            KrateDialogHeader(
                tag = "Fresh install",
                icon = rememberVectorPainter(Icons.Filled.AutoAwesome),
                title = "Start with Krate's picks?",
                body = installPromptBody(app, recommended.summary),
            ),
        onDismissRequest = { handled = true },
        footer = {
            TextButton(onClick = {
                RecommendedMarks.keptOwn(context, app.packageName, recommended.revision)
                handled = true
            }) { Text("Keep mine") }
            Button(onClick = {
                handled = true
                if (spec != null && helperOn) {
                    AutoBackupRunner.applyRecommended(app.packageName, spec, recommended)
                } else {
                    onOpenSheet()
                }
            }) { Text("Apply them") }
        },
    )
}

private fun installPromptBody(
    app: AppProfile,
    summary: List<String>,
): String {
    val first =
        summary.take(PREVIEW_PICKS).joinToString("; ") {
            it.trimEnd('.').replaceFirstChar { c ->
                c.lowercase()
            }
        }
    val more = summary.size - PREVIEW_PICKS
    val rest = if (more > 0) ", and $more more" else ""
    return "${app.displayName} is in. Krate can set it up the way it likes it: $first$rest. Anything else goes back " +
        "to its default, and your own settings are saved first, so you can always go back."
}
