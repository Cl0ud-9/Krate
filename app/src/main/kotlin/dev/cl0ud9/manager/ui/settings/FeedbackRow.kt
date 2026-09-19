package dev.cl0ud9.manager.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.util.managerViewModel
import java.net.URLEncoder

// GitHub issues has no attachment support over a plain URL, and a normal (non-system) app has no
// public API to read its own logcat anyway - so "send feedback with logs" here means: the user's
// own message plus a self-assembled diagnostic summary (device/app/catalog/recent-activity facts,
// not a raw system log), all inlined as text into a pre-filled GitHub issue body. Reusing the same
// repo manager already checks for its own updates against (ManagerUpdateChecker), not a separate
// support address
private const val FEEDBACK_REPO_URL = "https://github.com/Cl0ud-9/manager"

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
    managerViewModel { container ->
        SettingsViewModel(
            container.settingsRepository,
            container.artifactDownloader,
            container.managerUpdateChecker,
            container.githubCredentialStore,
            container.catalogRepository,
            container.installedPackageReader,
            container.activityLogRepository,
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
                subtitle = "Tell us what's wrong, optionally with a diagnostic report attached",
                colors =
                    SettingsRowColors(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
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
    OutlinedTextField(
        value = state.feedbackText,
        onValueChange = onFeedbackTextChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("What's the issue or feedback?") },
        minLines = 3,
    )

    val report = state.diagnosticReport
    if (report == null) {
        FilledTonalButton(
            onClick = onGenerateReport,
            enabled = !state.generatingReport,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.generatingReport) {
                Box(modifier = Modifier.padding(end = 8.dp)) { LoadingIndicator(modifier = Modifier.heightIn(20.dp)) }
            }
            Text(if (state.generatingReport) "Generating..." else "Attach a diagnostic report")
        }
    } else {
        DiagnosticReportPreview(report = report)
    }

    Button(
        onClick = { context.startActivity(feedbackIntent(state.feedbackText, report)) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Send feedback on GitHub")
    }
}

@Composable
private fun DiagnosticReportPreview(report: String) {
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
            shape = ShapeCache.smooth12,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            SelectionContainer {
                Text(
                    text = report,
                    modifier =
                        Modifier
                            .heightIn(max = REPORT_PREVIEW_MAX_HEIGHT)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val REPORT_PREVIEW_MAX_HEIGHT = 160.dp

private fun feedbackIntent(
    feedbackText: String,
    diagnosticReport: String?,
): Intent {
    val body =
        buildString {
            append(feedbackText.ifBlank { "(describe the issue here)" })
            if (diagnosticReport != null) {
                appendLine()
                appendLine()
                appendLine("<details><summary>Diagnostic report</summary>")
                appendLine()
                appendLine("```")
                append(diagnosticReport)
                appendLine("```")
                appendLine("</details>")
            }
        }
    val url =
        "$FEEDBACK_REPO_URL/issues/new" +
            "?title=${urlEncode("Feedback")}" +
            "&body=${urlEncode(body)}"
    return Intent(Intent.ACTION_VIEW, Uri.parse(url))
}

private fun shareTextIntent(text: String): Intent =
    Intent(Intent.ACTION_SEND)
        .apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "App Manager diagnostic report")
            putExtra(Intent.EXTRA_TEXT, text)
        }.let { Intent.createChooser(it, "Share diagnostic report") }

private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
