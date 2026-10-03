package dev.cl0ud9.krate.platform.workers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.DownloadStatus
import dev.cl0ud9.krate.domain.model.latestArtifact
import dev.cl0ud9.krate.domain.repository.buildKey
import dev.cl0ud9.krate.platform.AppContainer
import dev.cl0ud9.krate.platform.appContainer
import dev.cl0ud9.krate.platform.autoupdate.AutoUpdateLedger
import dev.cl0ud9.krate.platform.autoupdate.AutoUpdateWorker
import dev.cl0ud9.krate.platform.autoupdate.autoUpdatePolicy
import dev.cl0ud9.krate.platform.autoupdate.autoUpdatesOn
import dev.cl0ud9.krate.platform.autoupdate.isOverdue
import dev.cl0ud9.krate.platform.selfupdate.KrateUpdateStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last

// WorkManager periodic fallback for missed update notifications, section 9/24/44.4 of the spec -
// the manifest is the source of truth regardless of whether FCM is ever wired up (it needs a Firebase
// project, a manual external setup step this can't run on its own), so this check stands on its own
class ManifestCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer()
        val catalogResult =
            runCatching {
                // explicit refresh(), not observeApps().first() - the repository caches its last
                // result for the app's whole process lifetime (shared across every screen), so a plain
                // .first() would just replay a possibly hours-old cached value instead of a real check
                container.catalogRepository.refresh()
                val apps = container.catalogRepository.observeApps().first()
                val hasToken = container.githubCredentialStore.getToken() != null
                val baselines = container.krateBaselineStore.observeBaselines().first()
                apps to pendingUpdates(apps, container.installedPackageReader, hasToken, baselines)
            }.onSuccess { (apps, pending) ->
                val downloaded = pending.isNotEmpty() && downloadInBackground(container, pending, apps.map { it.id })
                // updates that go on by themselves aren't waiting on the user, so they don't notify
                val byHand = installOrLeave(apps, pending)
                if (byHand.isEmpty()) {
                    UpdateNotifier.clearPendingUpdates(applicationContext)
                } else {
                    UpdateNotifier.notifyPendingUpdates(
                        applicationContext,
                        byHand,
                        pendingUpdatesSignature(byHand),
                        downloaded,
                    )
                }
            }

        // amendment 44.2: independent of the catalog check above and never lets a failure here turn
        // an otherwise-successful periodic check into a retry - this is a nice-to-have addition,
        // not the worker's primary job
        runCatching { container.krateUpdateChecker.check() }
            .onSuccess { status ->
                if (status is KrateUpdateStatus.UpdateAvailable) {
                    UpdateNotifier.notifyKrateUpdateAvailable(applicationContext, status.latestVersion)
                }
            }

        return if (catalogResult.isSuccess) Result.success() else Result.retry()
    }

    // queues the background install of what's due; returns the updates left for the user
    private suspend fun installOrLeave(
        apps: List<AppProfile>,
        pending: List<AppProfile>,
    ): List<AppProfile> {
        if (pending.isEmpty() || !autoUpdatesOn(applicationContext)) return pending
        val policy = autoUpdatePolicy(applicationContext, apps)
        val now = System.currentTimeMillis()
        val due = pending.filter { policy.isDue(it, now) }
        if (due.isNotEmpty()) AutoUpdateWorker.enqueue(applicationContext)
        val ledger = AutoUpdateLedger(applicationContext)
        val overdue =
            due.filter { app ->
                val key = app.latestArtifact?.buildKey
                key != null && isOverdue(ledger.dueSince(app.id, key, now), now)
            }
        return pending.filter { !policy.willUpdate(it) || it in overdue }
    }

    // Settings > Automatic downloads: pre-fetch updates on Wi-Fi (or mobile data if allowed), not in Battery Saver;
    // true when all are ready
    private suspend fun downloadInBackground(
        container: AppContainer,
        pending: List<AppProfile>,
        allAppIds: List<String>,
    ): Boolean {
        val settings = container.settingsRepository
        if (!settings.observeAutomaticDownloads().first()) return false
        val allowed = canDownloadNow(mobileDataAllowed = settings.observeDownloadOnMobileData().first())
        // map before all: one failed download must not stop the rest from being tried
        return pending
            .map { app ->
                val artifact = app.latestArtifact ?: return@map false
                val downloader = container.artifactDownloader
                val ready =
                    downloader.existingReadyFile(app, artifact) != null ||
                        (
                            allowed &&
                                runCatching {
                                    downloader
                                        .download(
                                            app,
                                            artifact,
                                        ).last() is DownloadStatus.ReadyToInstall
                                }.getOrDefault(false)
                        )
                if (ready) {
                    downloader.pruneOtherBuilds(app, artifact, allAppIds)
                    container.activityLogRepository.clearFailures(app.id, downloadsOnly = true)
                }
                ready
            }.all { it }
    }

    // mobile data only when the user allowed it, and never while roaming or with Data Saver on
    private fun canDownloadNow(mobileDataAllowed: Boolean): Boolean {
        val connectivity = applicationContext.getSystemService(ConnectivityManager::class.java)
        val power = applicationContext.getSystemService(PowerManager::class.java)
        if (connectivity == null || power?.isPowerSaveMode == true) return false
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        return backgroundDownloadAllowed(
            metered = connectivity.isActiveNetworkMetered,
            roaming = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) == false,
            dataSaver = connectivity.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED,
            mobileDataAllowed = mobileDataAllowed,
        )
    }
}

// Wi-Fi (unmetered) always; a metered network only when allowed, not roaming and not under Data Saver
internal fun backgroundDownloadAllowed(
    metered: Boolean,
    roaming: Boolean,
    dataSaver: Boolean,
    mobileDataAllowed: Boolean,
): Boolean = !metered || (mobileDataAllowed && !roaming && !dataSaver)
