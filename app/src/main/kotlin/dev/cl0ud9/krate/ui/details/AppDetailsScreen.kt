package dev.cl0ud9.krate.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.Color
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
import dev.cl0ud9.krate.domain.model.isTracked
import dev.cl0ud9.krate.domain.model.latestArtifact
import dev.cl0ud9.krate.domain.repository.Baseline
import dev.cl0ud9.krate.domain.repository.effectiveBaseline
import dev.cl0ud9.krate.domain.repository.forTrack
import dev.cl0ud9.krate.domain.repository.isNewerThan
import dev.cl0ud9.krate.platform.packageinfo.InstalledVersion
import dev.cl0ud9.krate.ui.components.AnnouncementCard
import dev.cl0ud9.krate.ui.components.AppIconAvatar
import dev.cl0ud9.krate.ui.components.rememberAppIcon
import dev.cl0ud9.krate.ui.components.systemNavBarClearance
import dev.cl0ud9.krate.ui.home.PlayProtectReminder
import dev.cl0ud9.krate.ui.navigation.DetailContentTopGap
import dev.cl0ud9.krate.ui.navigation.IconFrost
import dev.cl0ud9.krate.ui.navigation.LocalLiquidGlass
import dev.cl0ud9.krate.ui.navigation.PageIconSize
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.navigation.sharedDetailsIcon
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.theme.rememberIconAccent
import dev.cl0ud9.krate.ui.util.RefreshOnResume
import dev.cl0ud9.krate.ui.util.krateViewModel
import dev.cl0ud9.krate.ui.util.rememberDebouncedOnClick

// how the setup and auto-update cards arrive and leave: unfolding, never a jump
private val CardIn = fadeIn() + expandVertically()
private val CardOut = fadeOut() + shrinkVertically()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppDetailsScreen(
    appId: String,
    onNavigateToApp: (String) -> Unit,
    scrollState: ScrollState,
    topContentPadding: Dp,
) {
    val viewModel = rememberAppDetailsViewModel(appId)
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
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val currentApp = app.takeIf { ready }
    // no pull-to-refresh here - RefreshOnResume above already re-checks this one app whenever the
    // screen comes back into view, so a swipe gesture on top of that was a redundant second trigger
    Box(modifier = Modifier.fillMaxSize()) {
        if (currentApp == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (missing) GoneApp() else LoadingIndicator()
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
                        justInstalled = viewModel.justInstalled.collectAsStateWithLifecycle().value,
                        restorePending = viewModel.restorePending.collectAsStateWithLifecycle().value,
                    ),
                onDismissAnnouncement = viewModel::dismissAnnouncement,
                onDownload = rememberDebouncedOnClick(onClick = viewModel::startDownload),
                onInstall = rememberDebouncedOnClick(onClick = viewModel::startInstall),
                onRetryAsCleanInstall = rememberDebouncedOnClick(onClick = viewModel::retryAsCleanInstall),
                onCancelDownload = viewModel::cancelDownload,
                onBackToLatest = viewModel::backToLatest,
                onUninstall = rememberDebouncedOnClick(onClick = viewModel::startUninstall),
                onSelectVersion = viewModel::selectVersion,
                onNavigateToApp = onNavigateToApp,
                scrollState = scrollState,
                topContentPadding = topContentPadding,
            )
        }
    }
}

@Composable
private fun rememberAppDetailsViewModel(appId: String): AppDetailsViewModel =
    krateViewModel { container ->
        AppDetailsViewModel(
            container.catalogRepository,
            container.artifactDownloader,
            container.installedPackageReader,
            container.activityLogRepository,
            container.krateBaselineStore,
            container.announcementDismissalStore,
            container.appWork,
            appId,
        )
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
    // an install just finished on this screen; its message shows where "Up to date." would
    val justInstalled: Boolean = false,
    // that install erased the app's data, so the setup card offers to bring the settings back
    val restorePending: Boolean = false,
) {
    val installedVersionName: String?
        get() = installed?.versionName

    // one tap downloads and installs; otherwise the button only downloads and installing is its own step
    val installsInPlace: Boolean
        get() = canInstallInPlace(app, installed, selectedArtifact)

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

    // the newer build of the installed theme waiting to go on, if there is one
    val pendingUpdate: ArtifactInfo?
        get() {
            val baseline = effectiveBaseline?.takeIf { installed != null } ?: return null
            return trackApp.latestArtifact?.takeIf { it.isNewerThan(baseline, trackApp.artifacts) }
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

    // the installed copy came from somewhere else, signed with its own key
    val signedDifferently: Boolean
        get() = isSignedDifferently(installed, selectedArtifact)

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

// a baseline recorded before builds had ids only knows its version, which is enough for apps that
// publish one build per version
internal fun ArtifactInfo.isBuildOf(baseline: Baseline): Boolean =
    if (baseline.buildId != null) buildId == baseline.buildId else versionName == baseline.versionName

// "20.40.45, build v6.2.1, Material You" for an app Krate builds itself, where the build number is what tells
// builds apart; just the version otherwise
internal fun ArtifactInfo.buildDescription(): String {
    val patches = patchesVersionName ?: return listOfNotNull(versionName, label).joinToString(", ")
    return listOfNotNull(versionName, "build $patches", label).joinToString(", ")
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
    onBackToLatest: () -> Unit,
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
    // picking a version resizes the action above the list and the notes below it; the list stays under the finger
    val anchor = rememberScrollAnchor(scrollState)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .anchoredPage(anchor)
                .padding(
                    top = topContentPadding + DetailContentTopGap,
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 20.dp + systemNavBarClearance,
                ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // everything above the version list, in one block the list keeps its place against
        Column(modifier = Modifier.aboveAnchor(anchor), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // name, version and installed status as one compact block
            AppDetailsHeader(
                app = app,
                installedVersionName = state.installedVersionName,
                uninstalling = uninstalling,
                onUninstall = onUninstall,
                moment = installMomentFor(state),
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
            val guarded = rememberBackupGuard(state, onInstall, onRetryAsCleanInstall)
            DownloadSection(
                state = state,
                onDownload = onDownload,
                onInstall = guarded.install,
                onRetryAsCleanInstall = guarded.retryFromScratch,
                onCancelDownload = onCancelDownload,
                onBackToLatest = onBackToLatest,
            )

            // what it needs switched on, and its settings, right under the action once it's installed
            // both fold away as an uninstall starts and unfold after an install, instead of the page jumping
            val guide = app.guide
            val onPhone = state.installed != null && !uninstalling
            AnimatedVisibility(visible = onPhone && guide != null, enter = CardIn, exit = CardOut) {
                guide?.let { SetupCard(app = app, guide = it, restorePending = state.restorePending) }
            }
            AnimatedVisibility(visible = onPhone, enter = CardIn, exit = CardOut) { AutoUpdateCard(state) }

            // Play Protect was paused for this app: once it's in, ask for it back on right here
            PlayProtectReminder(onlyFor = app.packageName)

            // a find of the person's own, high enough up to be seen: the nudge to suggest it for everyone
            if (app.isTracked) ShareFindCard(app)

            // what the app is sits right under the action, before the finer controls further down
            AppInfoSection(app = app, dependencies = state.dependencies, onNavigateToApp = onNavigateToApp)
        }

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
        olderThanInstalledListed = state.app.artifacts.any { isOlderThanInstalled(state.installed, it) },
        onSelectVersion = { artifact ->
            anchor.hold()
            onSelectVersion(artifact)
        },
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

// the app's icon beside its name and package
@Composable
private fun AppIdentity(
    app: AppProfile,
    moment: InstallMoment,
    accent: Color?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        InstallHalo(moment, accent) {
            AppIconAvatar(app = app, size = PageIconSize, modifier = Modifier.sharedDetailsIcon(app))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = app.displayName, style = MaterialTheme.typography.headlineSmall)
            Text(
                // a zero-width space after each dot lets a long name wrap between its parts at a big font
                text = app.packageName.replace(".", ".\u200B"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
    moment: InstallMoment,
) {
    Card(
        modifier = Modifier.fillMaxWidth().glassRim(ShapeCache.rounded16),
        shape = ShapeCache.rounded16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // with glass on, the app's own icon frosted behind its name, so the card reads as glass tinted by the app
        // with glass on the card is tinted by the app's icon, so its progress and badge take the app's own colour too
        val accent =
            if (LocalLiquidGlass.current) {
                rememberIconAccent(rememberAppIcon(app), MaterialTheme.colorScheme.surfaceContainer)
            } else {
                null
            }
        Box {
            IconFrost(rememberAppIcon(app))
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIdentity(app, moment, accent)

                        HeaderBadges(app, accent)

                        // only set for an app Krate builds itself: "Latest" above is the app's own version, and this
                        // build number sits beside it
                        app.latestArtifact?.patchesVersionName?.let { patchesVersion ->
                            val label = app.latestArtifact?.label
                            Text(
                                text = listOfNotNull(label, "Build $patchesVersion").joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // fades and folds away once the app is gone, rather than leaving a jump in the row
                    AnimatedVisibility(
                        visible = installedVersionName != null,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) { UninstallIconControl(uninstalling = uninstalling, onUninstall = onUninstall) }
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UninstallIconControl(
    uninstalling: Boolean,
    onUninstall: () -> Unit,
) {
    // one fixed slot, so trading the button for the spinner never nudges the name beside it
    Crossfade(targetState = uninstalling, modifier = Modifier.size(32.dp), label = "uninstall-control") { busy ->
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
            if (busy) {
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
    }
}

@Composable
private fun InstalledStatusRow(installedVersionName: String?) {
    val text = installedVersionName?.let { "Installed: version $it" } ?: "Not installed on this device"
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
