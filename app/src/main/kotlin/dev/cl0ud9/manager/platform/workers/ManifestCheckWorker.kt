package dev.cl0ud9.manager.platform.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.cl0ud9.manager.platform.appContainer
import dev.cl0ud9.manager.platform.selfupdate.ManagerUpdateStatus
import kotlinx.coroutines.flow.first

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
                // explicit refresh(), not observeApps().first() - the repository now caches its last
                // result for the app's whole process lifetime (shared across every screen), so a plain
                // .first() would just replay a possibly hours-old cached value forever instead of a real
                // background check
                container.catalogRepository.refresh()
                val apps = container.catalogRepository.observeApps().first()
                pendingUpdateCount(apps, container.installedPackageReader)
            }.onSuccess { count ->
                if (count > 0) UpdateNotifier.notifyPendingUpdates(applicationContext, count)
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
}
