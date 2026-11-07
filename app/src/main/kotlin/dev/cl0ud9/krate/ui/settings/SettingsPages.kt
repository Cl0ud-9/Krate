package dev.cl0ud9.krate.ui.settings

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.platform.autoupdate.AutoUpdateWorker
import dev.cl0ud9.krate.platform.autoupdate.autoUpdatesSupported
import dev.cl0ud9.krate.platform.autoupdate.isOpenEnded
import dev.cl0ud9.krate.platform.autoupdate.pausedUntil
import dev.cl0ud9.krate.ui.components.systemNavBarClearance
import dev.cl0ud9.krate.ui.navigation.DetailContentTopGap
import dev.cl0ud9.krate.ui.util.rememberDebouncedButtonState

// Automatic downloads and Use mobile data, which every phone has; the install rows add three more on Android 12
private const val UPDATE_ROWS = 2
private const val INSTALL_ROWS = 3

// a little past the end of a pause, so the run finds it over
private const val PAUSE_RUN_SLACK_MS = 60_000L

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
        Spacer(modifier = Modifier.height(24.dp + systemNavBarClearance))
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
    val onMobileData by viewModel.downloadOnMobileData.collectAsStateWithLifecycle()
    val autoInstall by viewModel.autoInstallUpdates.collectAsStateWithLifecycle()
    val cacheClearedMessage by viewModel.cacheClearedMessage.collectAsStateWithLifecycle()
    // idempotent in the ViewModel too - the debounce stops a double tap reaching it at all
    val clearCacheState = rememberDebouncedButtonState(onClick = viewModel::clearCache)
    var delaySheet by rememberSaveable { mutableStateOf(false) }
    val updateRows = UPDATE_ROWS + (if (autoUpdatesSupported) INSTALL_ROWS else 0)
    SettingsPage(scrollState, topContentPadding) {
        SettingsSectionLabel("Automatic updates", first = true)
        AutomaticDownloadsRow(
            checked = automaticDownloads,
            onCheckedChange = viewModel::setAutomaticDownloads,
            shape = settingsGroupShape(0, updateRows),
            onMobileData = automaticDownloads && onMobileData,
            autoInstall = autoUpdatesSupported && automaticDownloads && autoInstall,
        )
        MobileDataDownloadsRow(
            checked = onMobileData,
            available = automaticDownloads,
            onCheckedChange = viewModel::setDownloadOnMobileData,
            shape = settingsGroupShape(1, updateRows),
        )
        if (autoUpdatesSupported) {
            InstallRows(viewModel, firstRow = UPDATE_ROWS, rows = updateRows) { delaySheet = true }
        }
        SettingsSectionLabel("Storage")
        StorageRow(
            cacheClearedMessage = cacheClearedMessage,
            clearCacheState = clearCacheState,
            shape = settingsGroupShape(0, 1),
        )
    }
    if (delaySheet) InstallDelayPicker(viewModel) { delaySheet = false }
}

// installing by itself, how long to wait first, and the pause: only on Android 12 and newer
@Composable
private fun InstallRows(
    viewModel: SettingsViewModel,
    firstRow: Int,
    rows: Int,
    onPickDelay: () -> Unit,
) {
    val automaticDownloads by viewModel.automaticDownloads.collectAsStateWithLifecycle()
    val autoInstall by viewModel.autoInstallUpdates.collectAsStateWithLifecycle()
    val pausedUntil by viewModel.autoUpdatePausedUntil.collectAsStateWithLifecycle()
    val installDelay by viewModel.installDelay.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // the wait and the pause only mean something while automatic updates are on
    val installing = automaticDownloads && autoInstall
    AutoInstallRow(
        checked = autoInstall,
        available = automaticDownloads,
        onCheckedChange = viewModel::setAutoInstallUpdates,
        shape = settingsGroupShape(firstRow, rows),
    )
    InstallDelayRow(
        delay = installDelay,
        available = installing,
        shape = settingsGroupShape(firstRow + 1, rows),
        onClick = onPickDelay,
    )
    PauseAutoUpdatesRow(
        pausedUntil = pausedUntil,
        available = installing,
        onPause = { length ->
            val until = pausedUntil(System.currentTimeMillis(), length)
            viewModel.setAutoUpdatePausedUntil(until)
            // a run booked for the moment it ends, so due updates go on then rather than at the next check
            if (!isOpenEnded(until)) {
                AutoUpdateWorker.enqueueAt(context, until - System.currentTimeMillis() + PAUSE_RUN_SLACK_MS)
            }
        },
        onResume = {
            viewModel.setAutoUpdatePausedUntil(0L)
            AutoUpdateWorker.enqueue(context)
        },
        shape = settingsGroupShape(firstRow + 2, rows),
    )
}

@Composable
fun GitHubAccessPage(
    scrollState: ScrollState,
    topContentPadding: Dp,
) {
    val viewModel = rememberSettingsViewModel()
    val hasGitHubToken by viewModel.hasGitHubToken.collectAsStateWithLifecycle()
    val inviteStatus by viewModel.inviteStatus.collectAsStateWithLifecycle()
    SettingsPage(scrollState, topContentPadding) {
        GitHubAccessRow(
            hasToken = hasGitHubToken,
            inviteStatus = inviteStatus,
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

// shown above the categories while a Krate update is waiting - leads to About's Update button
@Composable
internal fun KrateUpdateBanner(
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
