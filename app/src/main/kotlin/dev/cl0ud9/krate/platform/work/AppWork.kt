package dev.cl0ud9.krate.platform.work

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dev.cl0ud9.krate.data.activity.DOWNLOAD_FAILURE_PREFIX
import dev.cl0ud9.krate.data.activity.INSTALL_FAILURE_PREFIX
import dev.cl0ud9.krate.data.downloads.ArtifactDownloader
import dev.cl0ud9.krate.data.downloads.DownloadProgressNotifier
import dev.cl0ud9.krate.domain.dependency.DependencyGraph
import dev.cl0ud9.krate.domain.installer.CleanInstallOrchestrator
import dev.cl0ud9.krate.domain.installer.InstallationEngine
import dev.cl0ud9.krate.domain.model.ActivityAction
import dev.cl0ud9.krate.domain.model.ActivityEntry
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.ArtifactInfo
import dev.cl0ud9.krate.domain.model.DownloadStatus
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.domain.repository.ActivityLogRepository
import dev.cl0ud9.krate.domain.repository.AutoUpdateStore
import dev.cl0ud9.krate.domain.repository.CatalogRepository
import dev.cl0ud9.krate.domain.repository.KrateBaselineStore
import dev.cl0ud9.krate.domain.repository.buildKey
import dev.cl0ud9.krate.domain.repository.newerThanPicked
import dev.cl0ud9.krate.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.krate.platform.selfupdate.notePlayProtect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// a failed attempt stays on screen at least this long, so Try again visibly does something
private const val MIN_ATTEMPT_VISIBLE_MS = 600L

// what's under way for one app: its download, its install or uninstall, and a count that moves on each time one
// goes through, so a page showing the app knows to settle and say so
data class AppWorkState(
    val download: DownloadStatus = DownloadStatus.Idle,
    val install: InstallStatus = InstallStatus.Idle,
    val finished: Finished? = null,
    // the build being downloaded or installed, so a page opened meanwhile shows that one rather than the newest
    val artifact: ArtifactInfo? = null,
)

// an install or uninstall that went through; serial tells one from the next
data class Finished(
    val serial: Long,
    val uninstalled: Boolean = false,
    // an existing app put back on after its data was erased, so the page offers its settings back
    val erasedData: Boolean = false,
)

// downloads, installs and uninstalls, kept going for as long as Krate runs rather than for as long as the app's page
// is open: leaving the page mid-download, or mid-way through a reinstall from scratch, no longer stops it half way.
// Pages only watch it and ask it for things
// LongParameterList and TooManyFunctions: one owner for everything an install touches (the download, the installer,
// the log, the baseline, the auto-update choices), and one entry point per thing a page can ask for
@Suppress("LongParameterList", "TooManyFunctions")
class AppWork(
    private val context: Context,
    private val downloader: ArtifactDownloader,
    private val notifier: DownloadProgressNotifier,
    private val engine: InstallationEngine,
    private val orchestrator: CleanInstallOrchestrator,
    private val activityLog: ActivityLogRepository,
    private val baselines: KrateBaselineStore,
    private val autoUpdates: AutoUpdateStore,
    private val catalog: CatalogRepository,
    private val installed: InstalledPackageReader,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val states = MutableStateFlow<Map<String, AppWorkState>>(emptyMap())
    private val downloads = ConcurrentHashMap<String, Job>()
    private var serial = 0L

    // every app with something under way, for lists to show progress on their rows
    val all: StateFlow<Map<String, AppWorkState>> = states.asStateFlow()

    fun state(appId: String): Flow<AppWorkState> = states.map { it[appId] ?: AppWorkState() }.distinctUntilChanged()

    fun current(appId: String): AppWorkState = states.value[appId] ?: AppWorkState()

    fun setDownload(
        appId: String,
        status: DownloadStatus,
    ) = change(appId) { it.copy(download = status) }

    fun setInstall(
        appId: String,
        status: InstallStatus,
    ) = change(appId) { it.copy(install = status) }

    fun busy(appId: String): Boolean =
        when (current(appId).install) {
            InstallStatus.Installing, InstallStatus.PreparingRollback,
            InstallStatus.Uninstalling, InstallStatus.RollingBack,
            is InstallStatus.WaitingForUser,
            -> true
            else -> false
        }

    fun downloading(appId: String): Boolean =
        current(appId).download.let { it is DownloadStatus.Downloading || it is DownloadStatus.Verifying }

    // [installAfter]: Install, Update or Reinstall means the whole thing, so the install follows by itself while
    // Krate is on screen; a download that finishes in the background waits behind its notification instead
    fun download(
        app: AppProfile,
        artifact: ArtifactInfo,
        installAfter: Boolean,
    ) {
        if (downloading(app.id) || busy(app.id)) return
        change(app.id) {
            AppWorkState(download = DownloadStatus.Downloading(0L, null), finished = it.finished, artifact = artifact)
        }
        // told at once, while Krate is on screen: that's when it can keep the network for the whole download
        notifier.onDownloading(app.id, app.displayName, 0L, null)
        downloads[app.id] =
            scope.launch {
                val startedAt = System.currentTimeMillis()
                downloader.download(app, artifact).collect { status ->
                    if (status is DownloadStatus.Failed) {
                        delay(
                            MIN_ATTEMPT_VISIBLE_MS - (System.currentTimeMillis() - startedAt),
                        )
                    }
                    setDownload(app.id, status)
                    downloaded(app, artifact, status, installAfter)
                }
                downloads.remove(app.id)
            }
    }

    // the partial file is kept, so starting again resumes it
    fun cancelDownload(appId: String) {
        downloads.remove(appId)?.cancel()
        setDownload(appId, DownloadStatus.Idle)
        notifier.clear(appId)
    }

    // in place, or from scratch through the orchestrator (uninstall first, with the old version kept to put back if
    // the install fails)
    fun install(
        app: AppProfile,
        apk: File,
        artifact: ArtifactInfo?,
        fromScratch: Boolean,
    ) {
        if (busy(app.id)) return
        // read before anything changes: whether this is an install or an update
        val action =
            if (installed.installedVersion(app.packageName) !=
                null
            ) {
                ActivityAction.UPDATED
            } else {
                ActivityAction.INSTALLED
            }
        val flow = if (fromScratch) orchestrator.cleanInstall(app, apk) else engine.install(app, apk)
        change(app.id) { it.copy(install = InstallStatus.Installing, artifact = artifact ?: it.artifact) }
        scope.launch {
            flow.collect { status ->
                if (status is InstallStatus.Success) {
                    succeeded(app, apk, artifact, erasedData = fromScratch && action == ActivityAction.UPDATED, action)
                } else {
                    setInstall(app.id, status)
                    failed(app, status)
                }
            }
        }
    }

    fun uninstall(app: AppProfile) {
        if (busy(app.id)) return
        scope.launch {
            engine.uninstall(app.packageName).collect { status ->
                if (status is InstallStatus.Success) {
                    record(app, ActivityAction.UNINSTALLED)
                    baselines.clear(app.packageName)
                    change(
                        app.id,
                    ) { it.copy(install = InstallStatus.Idle, finished = Finished(++serial, uninstalled = true)) }
                } else {
                    setInstall(app.id, status)
                }
            }
        }
    }

    private suspend fun downloaded(
        app: AppProfile,
        artifact: ArtifactInfo,
        status: DownloadStatus,
        installAfter: Boolean,
    ) {
        when (status) {
            is DownloadStatus.Downloading ->
                notifier.onDownloading(app.id, app.displayName, status.bytesDownloaded, status.totalBytes)
            is DownloadStatus.Verifying -> notifier.onVerifying(app.id, app.displayName)
            is DownloadStatus.ReadyToInstall -> {
                notifier.onComplete(app)
                activityLog.clearFailures(app.id, downloadsOnly = true)
                if (installAfter && onScreen() && dependenciesInstalled(app)) {
                    install(app, File(status.filePath), artifact, fromScratch = false)
                }
            }
            is DownloadStatus.Failed -> {
                notifier.onFailed(app, status.reason)
                record(app, ActivityAction.FAILED, "$DOWNLOAD_FAILURE_PREFIX ${status.reason}")
            }
            is DownloadStatus.Idle -> notifier.clear(app.id)
        }
    }

    private suspend fun succeeded(
        app: AppProfile,
        apk: File,
        artifact: ArtifactInfo?,
        erasedData: Boolean,
        action: ActivityAction,
    ) {
        // redundant once Android has it; kept on failure, so a retry reuses it instead of downloading again
        downloader.deleteDownloadedFile(apk.path)
        activityLog.clearFailures(app.id)
        record(app, action)
        notePlayProtect(context, app.packageName, stopped = false)
        // what Krate really installed now, a fact in place of any guess
        if (artifact != null) {
            baselines.recordInstall(app.packageName, artifact)
            // a rollback stays put: the build it stepped back from never comes back by itself
            app.newerThanPicked(artifact)?.let { autoUpdates.skip(app.id, it.buildKey) }
        }
        // the download stays as it was until the page settles, so Install never flashes up in between
        change(
            app.id,
        ) { it.copy(install = InstallStatus.Success, finished = Finished(++serial, erasedData = erasedData)) }
    }

    private suspend fun failed(
        app: AppProfile,
        status: InstallStatus,
    ) {
        if (status !is InstallStatus.Failed) return
        if (status.blockedByPlayProtect) notePlayProtect(context, app.packageName, stopped = true)
        if (!status.userCancelled) record(app, ActivityAction.FAILED, "$INSTALL_FAILURE_PREFIX ${status.reason}")
    }

    private suspend fun dependenciesInstalled(app: AppProfile): Boolean =
        DependencyGraph.directDependencies(app, catalog.observeApps().first()).all {
            installed.installedVersion(it.packageName) != null
        }

    private fun onScreen(): Boolean =
        ProcessLifecycleOwner
            .get()
            .lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)

    private suspend fun record(
        app: AppProfile,
        action: ActivityAction,
        detail: String? = null,
    ) {
        activityLog.record(
            ActivityEntry(
                id = UUID.randomUUID().toString(),
                appId = app.id,
                appName = app.displayName,
                action = action,
                timestampMillis = System.currentTimeMillis(),
                detail = detail,
                packageName = app.packageName,
            ),
        )
    }

    private fun change(
        appId: String,
        transform: (AppWorkState) -> AppWorkState,
    ) {
        states.update { all -> all + (appId to transform(all[appId] ?: AppWorkState())) }
    }
}
