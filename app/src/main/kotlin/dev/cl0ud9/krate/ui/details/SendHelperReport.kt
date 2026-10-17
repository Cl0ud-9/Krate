package dev.cl0ud9.krate.ui.details

import android.content.Context
import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.platform.backup.AutoBackupState
import dev.cl0ud9.krate.platform.diagnostics.KrateLog
import dev.cl0ud9.krate.ui.settings.rememberDeviceSummary

private const val LOG_LINES = 60

// what went wrong with the helper, on a phone it can be told about: the app, the reason shown, the device facts and the
// last of Krate's own log (no settings, tokens or web addresses in any of it), as plain text to send from any app
internal fun helperReportText(
    app: AppProfile,
    failure: AutoBackupState.Failed,
    deviceSummary: String,
    log: String,
): String =
    buildString {
        appendLine("Krate's helper stopped.")
        appendLine("App: ${app.displayName} (${app.packageName})")
        appendLine("Doing: ${failure.mode.name.lowercase()}")
        appendLine("Reason shown: ${failure.reason}")
        appendLine()
        append(deviceSummary)
        if (log.isNotBlank()) {
            appendLine()
            appendLine("Krate's own log (newest last):")
            appendLine(log)
        }
    }

// only after a helper run has failed for this app: a small button that sends the report, so a bug on someone's phone
// reaches the person who can fix it with what's needed to
@Composable
internal fun SendHelperReport(
    app: AppProfile,
    state: AutoBackupState?,
) {
    val failure = state as? AutoBackupState.Failed ?: return
    val context = LocalContext.current
    val summary = rememberDeviceSummary()
    TextButton(onClick = { context.sendReport(app, failure, summary) }) { Text("Send a report") }
}

private fun Context.sendReport(
    app: AppProfile,
    failure: AutoBackupState.Failed,
    deviceSummary: String,
) {
    val body = helperReportText(app, failure, deviceSummary, KrateLog.read(LOG_LINES))
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Krate helper report: ${app.displayName}")
            putExtra(Intent.EXTRA_TEXT, body)
        }
    runCatching {
        startActivity(
            Intent.createChooser(send, "Send report with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
