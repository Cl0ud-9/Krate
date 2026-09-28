package dev.cl0ud9.krate.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.ui.components.BusyButtonContent
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.MAX_ISSUE_URL_LENGTH
import dev.cl0ud9.krate.ui.util.githubNewIssueUrl
import dev.cl0ud9.krate.ui.util.issueTitle
import dev.cl0ud9.krate.ui.util.krateViewModel
import dev.cl0ud9.krate.ui.util.ownsScroll

private const val FEEDBACK_MAX_LINES = 8

// bundles the row's own state, keeping FeedbackRow/FeedbackRowContent under detekt's
// parameter-count threshold without losing each value's own name at the call site
internal data class FeedbackUiState(
    val feedbackText: String,
    val diagnosticReport: String?,
    val generatingReport: Boolean,
)

// shared by SettingsScreen and AppearanceRoute, which both need the same ViewModel instance
// pointed at the same underlying settings - keeping the construction in one place means a new
// constructor param (like this feature's own three) only ever needs updating here
@Composable
internal fun rememberSettingsViewModel(): SettingsViewModel =
    krateViewModel { container ->
        SettingsViewModel(
            container.settingsRepository,
            container.artifactDownloader,
            container.krateUpdateChecker,
            container.krateSelfUpdateInstaller,
            container.githubCredentialStore,
            container.catalogRepository,
            container.installedPackageReader,
            container.activityLogRepository,
            container.krateBaselineStore,
            container.privateCatalogSource.status,
        )
    }

@Composable
internal fun rememberFeedbackUiState(viewModel: SettingsViewModel): FeedbackUiState {
    val feedbackText by viewModel.feedbackText.collectAsStateWithLifecycle()
    val diagnosticReport by viewModel.diagnosticReport.collectAsStateWithLifecycle()
    val generatingReport by viewModel.generatingReport.collectAsStateWithLifecycle()
    return FeedbackUiState(feedbackText, diagnosticReport, generatingReport)
}

@Composable
internal fun FeedbackRow(
    state: FeedbackUiState,
    onFeedbackTextChange: (String) -> Unit,
    onGenerateReport: () -> Unit,
    shape: Shape,
) {
    SettingsRow(
        header =
            SettingsRowHeader(
                icon = painterResource(R.drawable.ic_feedback_rounded),
                title = "Feedback & bug reports",
                subtitle = "Describe the problem and send it from any app, like email or WhatsApp",
                colors = SettingsTint.AMBER.colors(),
            ),
        shape = shape,
    ) {
        FeedbackRowContent(
            state = state,
            onFeedbackTextChange = onFeedbackTextChange,
            onGenerateReport = onGenerateReport,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FeedbackRowContent(
    state: FeedbackUiState,
    onFeedbackTextChange: (String) -> Unit,
    onGenerateReport: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val footer = rememberFeedbackFooter()
    // grows to a few lines, then scrolls inside itself without moving the page
    val feedback = rememberTextFieldState(state.feedbackText)
    val feedbackScroll = rememberScrollState()
    LaunchedEffect(feedback) { snapshotFlow { feedback.text.toString() }.collect(onFeedbackTextChange) }
    OutlinedTextField(
        state = feedback,
        modifier = Modifier.fillMaxWidth().ownsScroll(feedbackScroll),
        label = { Text("What's the issue or feedback?") },
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = FEEDBACK_MAX_LINES),
        scrollState = feedbackScroll,
    )

    val report = state.diagnosticReport
    if (report == null) {
        FilledTonalButton(
            onClick = { if (!state.generatingReport) onGenerateReport() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            BusyButtonContent(
                busy = state.generatingReport,
                text = "Attach a diagnostic report",
                busyText = "Generating...",
            )
        }
    } else {
        DiagnosticReportPreview(report = report)
    }

    // sent through the share sheet (email, WhatsApp, ...) - most people don't have a GitHub
    // account, so a GitHub issue is the secondary route, not the only one
    Button(
        onClick = { context.startActivity(shareFeedbackIntent(state.feedbackText, report, footer)) },
        enabled = state.feedbackText.isNotBlank() || report != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Send feedback")
    }
    TextButton(
        onClick = {
            val issue = feedbackIssue(state.feedbackText, report, footer)
            // a report too long for a link goes to the clipboard, and the issue says to paste it in
            if (issue.reportOnClipboard && report != null) clipboard.setText(AnnotatedString(report))
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(issue.url))) }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Report on GitHub instead")
    }
}

@Composable
private fun DiagnosticReportPreview(report: String) {
    val reportScroll = rememberScrollState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(report)) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    painter = rememberVectorPainter(Icons.Filled.ContentCopy),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(" Copy", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = { context.startActivity(shareTextIntent(report)) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    painter = rememberVectorPainter(Icons.Filled.Share),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(" Share", style = MaterialTheme.typography.labelLarge)
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeCache.rounded12,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            SelectionContainer {
                Text(
                    text = report,
                    modifier =
                        Modifier
                            .heightIn(max = REPORT_PREVIEW_MAX_HEIGHT)
                            .ownsScroll(reportScroll)
                            .verticalScroll(reportScroll)
                            .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val REPORT_PREVIEW_MAX_HEIGHT = 160.dp

// the version and phone a message came from, added even when no report is attached
@Composable
private fun rememberFeedbackFooter(): String {
    val version = rememberVersionName()
    return remember(
        version,
    ) { "Krate $version, Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL}" }
}

internal class FeedbackIssue(
    val url: String,
    val reportOnClipboard: Boolean,
)

// the GitHub issue: their words, the footer, and the report folded away under a summary
internal fun feedbackIssue(
    feedbackText: String,
    diagnosticReport: String?,
    footer: String,
): FeedbackIssue {
    val title = issueTitle("Feedback", feedbackText)
    val message = feedbackText.trim().ifEmpty { "(describe the issue here)" }

    fun body(report: String?): String =
        buildString {
            appendLine(message)
            appendLine()
            appendLine("---")
            append(footer)
            if (report != null) {
                appendLine()
                appendLine()
                appendLine("<details><summary>Diagnostic report</summary>")
                appendLine()
                appendLine("```")
                appendLine(report.trimEnd())
                appendLine("```")
                append("</details>")
            }
        }
    val full = githubNewIssueUrl(title, body(diagnosticReport))
    if (diagnosticReport == null || full.length <= MAX_ISSUE_URL_LENGTH) return FeedbackIssue(full, false)
    val pasteNote = "\n\n(The diagnostic report was copied to your clipboard. Paste it here.)"
    return FeedbackIssue(githubNewIssueUrl(title, body(null) + pasteNote), true)
}

// the message for any app: their words, then the report or at least the footer
internal fun feedbackShareText(
    feedbackText: String,
    diagnosticReport: String?,
    footer: String,
): String =
    buildString {
        val message = feedbackText.trim()
        if (message.isNotEmpty()) append(message).append("\n\n")
        append(diagnosticReport?.trimEnd() ?: "---\n$footer")
    }

private fun shareFeedbackIntent(
    feedbackText: String,
    diagnosticReport: String?,
    footer: String,
): Intent =
    Intent(Intent.ACTION_SEND)
        .apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, issueTitle("Krate feedback", feedbackText))
            putExtra(Intent.EXTRA_TEXT, feedbackShareText(feedbackText, diagnosticReport, footer))
        }.let { Intent.createChooser(it, "Send feedback with") }

private fun shareTextIntent(text: String): Intent =
    Intent(Intent.ACTION_SEND)
        .apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Krate diagnostic report")
            putExtra(Intent.EXTRA_TEXT, text)
        }.let { Intent.createChooser(it, "Share diagnostic report") }
