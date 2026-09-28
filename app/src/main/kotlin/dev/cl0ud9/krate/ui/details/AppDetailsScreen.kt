package dev.cl0ud9.krate.ui.details

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.AnnouncementItem
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.ArtifactInfo
import dev.cl0ud9.krate.domain.model.DownloadStatus
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.model.WaitingForUserStep
import dev.cl0ud9.krate.domain.model.latestArtifact
import dev.cl0ud9.krate.domain.repository.Baseline
import dev.cl0ud9.krate.domain.repository.effectiveBaseline
import dev.cl0ud9.krate.domain.repository.forTrack
import dev.cl0ud9.krate.domain.repository.isNewerThan
import dev.cl0ud9.krate.platform.packageinfo.InstalledVersion
import dev.cl0ud9.krate.ui.components.AnnouncementCard
import dev.cl0ud9.krate.ui.components.AppIconAvatar
import dev.cl0ud9.krate.ui.components.SupportStatusBadge
import dev.cl0ud9.krate.ui.components.systemNavBarClearance
import dev.cl0ud9.krate.ui.navigation.DetailContentTopGap
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.RefreshOnResume
import dev.cl0ud9.krate.ui.util.krateViewModel
import dev.cl0ud9.krate.ui.util.rememberDebouncedOnClick

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppDetailsScreen(
    appId: String,
    onNavigateToApp: (String) -> Unit,
    scrollState: ScrollState,
    topContentPadding: Dp,
) {
    val viewModel =
        krateViewModel { container ->
            AppDetailsViewModel(
                container.catalogRepository,
                container.artifactDownloader,
                container.installationEngine,
                container.cleanInstallOrchestrator,
                container.installedPackageReader,
                container.activityLogRepository,
                container.krateBaselineStore,
                container.downloadProgressNotifier,
                container.announcementDismissalStore,
                appId,
            )
        }
    RefreshOnResume(viewModel::refresh)
    val app by viewModel.app.collectAsStateWithLifecycle()
    val installedVersion by viewModel.installedVersion.collectAsStateWithLifecycle()
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val krateBaseline by viewModel.krateBaseline.collectAsStateWithLifecycle()
    val dependencies by viewModel.dependencies.collectAsStateWithLifecycle()
    val downloadStatus by viewModel.downloadStatus.collectAsStateWithLifecycle()
    val installStatus by viewModel.installStatus.collectAsStateWithLifecycle()
    val selectedArtifact by viewModel.selectedArtifact.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val currentApp = app.takeIf { ready }
    // no pull-to-refresh here - RefreshOnResume above already re-checks this one app whenever the
    // screen comes back into view, so a swipe gesture on top of that was a redundant second trigger
    Box(modifier = Modifier.fillMaxSize()) {
        if (currentApp == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        } else {
            // each is already idempotent in the ViewModel (isBusy()/status guards a second call
            // while one is running) - this debounce just stops a fast double-tap from reaching that
            // guard's race window at all
            AppDetailsContent(
                state =
                    AppDetailsUiState(
                        app = currentApp,
                        installed = installedVersion,
                        recordedBaseline = krateBaseline,
                        dependencies = dependencies,
                        downloadStatus = downloadStatus,
                        installStatus = installStatus,
                        selectedArtifact = selectedArtifact,
                        announcements = announcements,
                    ),
                onDismissAnnouncement = viewModel::dismissAnnouncement,
                onDownload = rememberDebouncedOnClick(onClick = viewModel::startDownload),
                onInstall = rememberDebouncedOnClick(onClick = viewModel::startInstall),
                onRetryAsCleanInstall = rememberDebouncedOnClick(onClick = viewModel::retryAsCleanInstall),
                onCancelDownload = viewModel::cancelDownload,
                onUninstall = rememberDebouncedOnClick(onClick = viewModel::startUninstall),
                onSelectVersion = viewModel::selectVersion,
                onNavigateToApp = onNavigateToApp,
                scrollState = scrollState,
                topContentPadding = topContentPadding,
            )
        }
    }
}

// bundles the screen's state so the composables below stay under the parameter-count limit
internal data class AppDetailsUiState(
    val app: AppProfile,
    val installed: InstalledVersion?,
    // the build Krate itself last installed, or null if it never has (see
    // KrateBaselineStore) - not the live installed version, and not necessarily catalog-known on
    // its own; effectiveBaseline below is what actually gets compared against
    val recordedBaseline: Baseline?,
    val dependencies: List<DependencyInfo>,
    val downloadStatus: DownloadStatus,
    val installStatus: InstallStatus,
    val selectedArtifact: ArtifactInfo?,
    val announcements: List<AnnouncementItem> = emptyList(),
) {
    val installedVersionName: String?
        get() = installed?.versionName

    // the recorded baseline if Krate has one, otherwise its best guess - see
    // KrateBaselineStore.effectiveBaseline for the full reasoning
    val effectiveBaseline: Baseline?
        get() = effectiveBaseline(recordedBaseline, app, installed)

    // just the installed theme's builds, for apps that publish several themes side by side
    val trackApp: AppProfile
        get() = app.forTrack(effectiveBaseline?.takeIf { installed != null })

    // the listed build that's on the device, when Krate knows exactly which one
    private val installedArtifact: ArtifactInfo?
        get() =
            effectiveBaseline?.takeIf { installed != null && it.buildId != null }?.let { baseline ->
                app.artifacts.firstOrNull { it.isBuildOf(baseline) }
            }

    // a build of another theme than the installed one - a change of look, not an update or a rollback
    val isSwitch: Boolean
        get() {
            val currentLabel = installedArtifact?.label
            val selected = selectedArtifact
            return currentLabel != null && selected != null && selected.label != currentLabel
        }

    // a build older than the one on the device - by version code when both are known, else by catalog order
    val isRollback: Boolean
        get() {
            val selected = selectedArtifact
            // the default pick is never a rollback, even when the installed build is newer than the catalog's
            return installed != null &&
                selected != null &&
                selected != trackApp.latestArtifact &&
                !isSwitch &&
                isOlderThanInstalled(selected)
        }

    private fun isOlderThanInstalled(selected: ArtifactInfo): Boolean {
        val code = selected.versionCode
        val baseline = effectiveBaseline
        return when {
            code != null && installed != null && code != installed.versionCode -> code < installed.versionCode
            baseline != null -> !selected.isNewerThan(baseline, trackApp.artifacts) && !selected.isBuildOf(baseline)
            else -> false
        }
    }

    // a build that was already going to uninstall first, so offering "from scratch" as a fallback would repeat it
    val installsFromScratch: Boolean
        get() = app.installationMode == InstallationMode.CLEAN_INSTALL || requiresUninstall

    // see requiresUninstall - shown as a warning before, and used to route the install through the
    // uninstall-first path
    val requiresUninstall: Boolean
        get() = requiresUninstall(installed, selectedArtifact)

    // read by both the Idle and Failed branches of the download section - whether the installed
    // app already matches what's selected is independent of whatever the current download
    // attempt's own status is, so a failed redownload shouldn't hide that the app is fine.
    // "up to date" compares the BASELINE against what's selected, not the live installed version -
    // a package can end up ahead of the catalog entirely outside this app (MicroG RE's own in-app
    // "hide icon" toggle installs its own beta build, for example), and by explicit product
    // decision that divergence alone should never manufacture an "Update" that doesn't genuinely
    // exist relative to what Krate last gave the user, nor hide one that does
    val isUpToDate: Boolean
        get() {
            val baseline = effectiveBaseline ?: return false
            val selected = selectedArtifact ?: return false
            return !selected.isNewerThan(baseline, trackApp.artifacts)
        }

    // true when the live-installed version doesn't match the baseline - something changed this
    // app's install outside Krate (that same MicroG RE toggle). Independent of isUpToDate:
    // this can be true whether or not there's also a real pending update, and drives an
    // informational note rather than any action of its own
    val isDiverged: Boolean
        get() {
            val installed = installedVersionName ?: return false
            val baseline = effectiveBaseline ?: return false
            return installed != baseline.versionName
        }
}

// Android refuses an in-place install of a lower versionCode, so a build older than the installed
// one can only go on after uninstalling it (which erases the app's data)
internal fun requiresUninstall(
    installed: InstalledVersion?,
    artifact: ArtifactInfo?,
): Boolean {
    val versionCode = artifact?.versionCode ?: return false
    return installed != null && versionCode < installed.versionCode
}

// a baseline recorded before builds had ids only knows its version, which is enough for apps that
// publish one build per version
internal fun ArtifactInfo.isBuildOf(baseline: Baseline): Boolean =
    if (baseline.buildId != null) buildId == baseline.buildId else versionName == baseline.versionName

// "patches v6.2.1 on 20.40.45, Material You" for a patched app, where the patches release is what
// tells builds apart; just the version otherwise
internal fun ArtifactInfo.buildDescription(): String {
    val patches = patchesVersionName ?: return listOfNotNull(versionName, label).joinToString(", ")
    return listOfNotNull("patches $patches on $versionName", label).joinToString(", ")
}

@Suppress("LongParameterList")
@Composable
private fun AppDetailsContent(
    state: AppDetailsUiState,
    onDismissAnnouncement: (String) -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetryAsCleanInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    onUninstall: () -> Unit,
    onSelectVersion: (ArtifactInfo) -> Unit,
    onNavigateToApp: (String) -> Unit,
    scrollState: ScrollState,
    topContentPadding: Dp,
) {
    val app = state.app
    val awaitingUninstallConfirm =
        state.installStatus is InstallStatus.WaitingForUser &&
            state.installStatus.step == WaitingForUserStep.UNINSTALL_CONFIRM
    val uninstalling = state.installStatus is InstallStatus.Uninstalling || awaitingUninstallConfirm
    // picking a version or collapsing long notes resizes what's above the version list; it stays under the finger
    val anchor = rememberScrollAnchor(scrollState)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    top = topContentPadding + DetailContentTopGap,
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 20.dp + systemNavBarClearance,
                ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // name, version and installed status as one compact block
        AppDetailsHeader(
            app = app,
            installedVersionName = state.installedVersionName,
            uninstalling = uninstalling,
            onUninstall = onUninstall,
        )

        state.announcements.forEach { item ->
            AnnouncementCard(item = item, onOpenApp = onNavigateToApp, onDismiss = onDismissAnnouncement)
        }

        // shown before the user ever reaches the Install button - a required dependency missing
        // (e.g. a sign-in helper another app relies on) means the app installs but silently fails to open,
        // so this is surfaced as early and as plainly as possible rather than only as a disabled
        // button and small helper text further down the page
        val unmetDependencies = state.dependencies.filter { !it.installed }
        if (unmetDependencies.isNotEmpty()) {
            MissingDependencyWarning(unmetDependencies = unmetDependencies, onNavigateToApp = onNavigateToApp)
        }

        // the primary action moves right under the header instead of sitting below Release notes,
        // which could push it off-screen for apps with long release notes - a detail page exists
        // to get the user to this action, so it should not be the thing they have to scroll to find
        DownloadSection(
            state = state,
            onDownload = onDownload,
            onInstall = onInstall,
            onRetryAsCleanInstall = onRetryAsCleanInstall,
            onCancelDownload = onCancelDownload,
        )

        // what the app is sits right under the action, before the finer controls further down
        AppInfoSection(app = app, dependencies = state.dependencies, onNavigateToApp = onNavigateToApp)

        NotesAndHistory(state = state, anchor = anchor, onSelectVersion = onSelectVersion)
    }
}

// every build to pick from, then the picked build's notes below it: a pick only ever changes what's under the list,
// so nothing the finger is on moves, and the notes can grow or shrink freely
@Composable
private fun NotesAndHistory(
    state: AppDetailsUiState,
    anchor: ScrollAnchor,
    onSelectVersion: (ArtifactInfo) -> Unit,
) {
    // a broken newest build can be worked around right away by picking an earlier one here
    VersionHistorySection(
        app = state.app,
        // a version changed outside Krate isn't any listed build, so nothing gets the Installed tag then
        installedBuild = state.effectiveBaseline?.takeIf { state.installed != null && !state.isDiverged },
        selectedArtifact = state.selectedArtifact,
        olderThanInstalledListed = state.app.artifacts.any { requiresUninstall(state.installed, it) },
        onSelectVersion = { artifact ->
            anchor.hold()
            onSelectVersion(artifact)
        },
        modifier = Modifier.anchoredBy(anchor),
    )
    ReleaseNotesSection(
        app = state.app,
        builds =
            releaseNotesBuilds(
                state.trackApp,
                state.selectedArtifact,
                state.effectiveBaseline?.takeIf {
                    state.installed !=
                        null
                },
            ),
    )
}

// the trash action sits beside the name/compatibility/version block as a whole, vertically centered
// against its full height (not pinned to the bottom "Installed" line) - a floating trailing action
// for the header overall, rather than a control that belongs to any one row within it
@Composable
private fun AppDetailsHeader(
    app: AppProfile,
    installedVersionName: String?,
    uninstalling: Boolean,
    onUninstall: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.rounded16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AppIconAvatar(app = app, size = 56.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = app.displayName, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SupportStatusBadge(status = app.supportStatus)
                        Text(
                            text = app.latestArtifact?.versionName?.let { "Latest $it" } ?: "Latest version unknown",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    // only set for an artifact built by an intermediate tool (a patches bundle) - "Latest"
                    // above is always the app's own version, so this is shown alongside it rather than
                    // instead of it, giving a complete picture of what was patched and what patched it
                    app.latestArtifact?.patchesVersionName?.let { patchesVersion ->
                        val label = app.latestArtifact?.label
                        Text(
                            text = listOfNotNull(label, "Patches $patchesVersion").joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (installedVersionName != null) {
                    UninstallIconControl(uninstalling = uninstalling, onUninstall = onUninstall)
                }
            }

            // the app's one-line summary, full width under the name block
            app.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            InstalledStatusRow(installedVersionName = installedVersionName)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UninstallIconControl(
    uninstalling: Boolean,
    onUninstall: () -> Unit,
) {
    if (uninstalling) {
        LoadingIndicator(modifier = Modifier.size(20.dp))
    } else {
        IconButton(onClick = onUninstall, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Uninstall app",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun InstalledStatusRow(installedVersionName: String?) {
    val text = installedVersionName?.let { "Installed - version $it" } ?: "Not installed on this device"
    val tint =
        if (installedVersionName != null) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    val icon = if (installedVersionName != null) painterResource(R.drawable.ic_check_circle_rounded) else null
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}
