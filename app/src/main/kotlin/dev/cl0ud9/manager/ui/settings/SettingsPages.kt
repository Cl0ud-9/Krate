package dev.cl0ud9.manager.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.ui.navigation.DetailContentTopGap
import dev.cl0ud9.manager.ui.util.rememberDebouncedButtonState

// the pages behind Settings' category rows - each holds one topic's controls, so the Settings list
// itself stays a short, scannable index rather than every control stacked on one long page

// the shared page frame: scrolls under the collapsing header, rows grouped 2dp apart
@Composable
internal fun SettingsPage(
    scrollState: ScrollState,
    topContentPadding: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = topContentPadding + DetailContentTopGap, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content()
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// a section title above a group of rows, as in Material's settings lists
@Composable
internal fun SettingsSectionLabel(
    title: String,
    first: Boolean = false,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = if (first) 4.dp else 20.dp, bottom = 8.dp),
    )
}

// a category row that opens its own page - the header's four parts are passed separately so each
// call site reads as the row it describes
@Suppress("LongParameterList")
@Composable
internal fun SettingsNavRow(
    icon: Painter,
    title: String,
    subtitle: String,
    colors: SettingsRowColors,
    shape: Shape,
    onClick: () -> Unit,
) {
    SettingsRow(
        header = SettingsRowHeader(icon = icon, title = title, subtitle = subtitle, colors = colors),
        shape = shape,
        onClick = onClick,
        trailing = { Icon(painterResource(R.drawable.ic_chevron_right_rounded), contentDescription = null) },
    )
}

@Composable
fun DownloadsStoragePage(
    scrollState: ScrollState,
    topContentPadding: Dp,
) {
    val viewModel = rememberSettingsViewModel()
    val automaticDownloads by viewModel.automaticDownloads.collectAsStateWithLifecycle()
    val cacheClearedMessage by viewModel.cacheClearedMessage.collectAsStateWithLifecycle()
    // idempotent in the ViewModel too - the debounce stops a double tap reaching it at all
    val clearCacheState = rememberDebouncedButtonState(onClick = viewModel::clearCache)
    SettingsPage(scrollState, topContentPadding) {
        AutomaticDownloadsRow(
            checked = automaticDownloads,
            onCheckedChange = viewModel::setAutomaticDownloads,
            shape = settingsGroupShape(0, 2),
        )
        StorageRow(
            cacheClearedMessage = cacheClearedMessage,
            clearCacheState = clearCacheState,
            shape = settingsGroupShape(1, 2),
        )
    }
}

@Composable
fun GitHubAccessPage(
    scrollState: ScrollState,
    topContentPadding: Dp,
) {
    val viewModel = rememberSettingsViewModel()
    val hasGitHubToken by viewModel.hasGitHubToken.collectAsStateWithLifecycle()
    SettingsPage(scrollState, topContentPadding) {
        GitHubAccessRow(
            hasToken = hasGitHubToken,
            onSaveToken = viewModel::setGitHubToken,
            onClearToken = viewModel::clearGitHubToken,
            shape = settingsGroupShape(0, 1),
        )
    }
}

@Composable
fun FeedbackPage(
    scrollState: ScrollState,
    topContentPadding: Dp,
) {
    val viewModel = rememberSettingsViewModel()
    val feedbackState = rememberFeedbackUiState(viewModel)
    val deviceSummary = rememberDeviceSummary()
    SettingsPage(scrollState, topContentPadding) {
        FeedbackRow(
            state = feedbackState,
            onFeedbackTextChange = viewModel::setFeedbackText,
            onGenerateReport = { viewModel.generateDiagnosticReport(deviceSummary) },
            shape = settingsGroupShape(0, 1),
        )
    }
}

// shown above the categories while a manager update is waiting - leads to About's Update button
@Composable
internal fun ManagerUpdateBanner(
    latestVersion: String,
    onClick: () -> Unit,
) {
    SettingsNavRow(
        icon = painterResource(R.drawable.ic_krate),
        title = "Krate $latestVersion is available",
        subtitle = "Tap to update",
        colors =
            SettingsRowColors(
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        shape = settingsGroupShape(0, 1),
        onClick = onClick,
    )
}
