package dev.cl0ud9.manager.platform.workers

import android.content.Context
import android.net.ConnectivityManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.DownloadStatus
import dev.cl0ud9.manager.domain.model.latestArtifact
import dev.cl0ud9.manager.platform.AppContainer
import dev.cl0ud9.manager.platform.appContainer
import dev.cl0ud9.manager.platform.selfupdate.ManagerUpdateStatus
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
                val baselines = container.managerBaselineStore.observeBaselines().first()
                pendingUpdates(apps, container.installedPackageReader, hasToken, baselines)
            }.onSuccess { pending ->
                if (pending.isEmpty()) {
                    UpdateNotifier.clearPendingUpdates(applicationContext)
                } else {
                    val downloaded = downloadInBackground(container, pending)
                    UpdateNotifier.notifyPendingUpdates(
                        applicationContext,
                        pending.size,
                        pendingUpdatesSignature(pending),
                        downloaded,
                    )
                }
            }

        // amendment 44.2: independent of the catalog check above and never lets a failure here turn
        // an otherwise-successful periodic check into a retry - this is a nice-to-have addition,
        // not the worker's primary job
        runCatching { container.managerUpdateChecker.check() }
            .onSuccess { status ->
                if (status is ManagerUpdateStatus.UpdateAvailable) {
                    UpdateNotifier.notifyManagerUpdateAvailable(applicationContext, status.latestVersion)
                }
            }

        return if (catalogResult.isSuccess) Result.success() else Result.retry()
    }

    // Settings > Automatic downloads: fetch and verify pending updates ahead of time so installing
    // is instant, only on an unmetered connection (Videoapp builds are ~170 MB). Installing still
    // always needs the user. True when every pending update ended up downloaded
    private suspend fun downloadInBackground(
        container: AppContainer,
        pending: List<AppProfile>,
    ): Boolean {
        val enabled = container.settingsRepository.observeAutomaticDownloads().first()
        val connectivity = applicationContext.getSystemService(ConnectivityManager::class.java)
        if (!enabled || connectivity == null || connectivity.isActiveNetworkMetered) return false
        // map before all: one failed download must not stop the rest from being tried
        return pending
            .map { app ->
                val artifact = app.latestArtifact ?: return@map false
                container.artifactDownloader.existingReadyFile(app, artifact) != null ||
                    runCatching {
                        container.artifactDownloader.download(app, artifact).last() is DownloadStatus.ReadyToInstall
                    }.getOrDefault(false)
            }.all { it }
    }
}
