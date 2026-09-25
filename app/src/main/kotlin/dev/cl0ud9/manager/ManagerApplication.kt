package dev.cl0ud9.manager

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.cl0ud9.manager.platform.AppContainer
import dev.cl0ud9.manager.platform.workers.ManifestCheckWorker
import dev.cl0ud9.manager.platform.workers.UpdateNotifier
import java.util.concurrent.TimeUnit

// section 40 of the spec: WorkManager periodic work is intentionally inexact. Every 6 hours is plenty -
// the catalog itself is regenerated every 2 hours and Patched builds land at most twice a day, and a
// pull-to-refresh in the app always checks right away. Stands on its own since FCM needs a Firebase
// project this app can't set up for itself
private const val MANIFEST_CHECK_INTERVAL_HOURS = 6L
private const val MANIFEST_CHECK_WORK_NAME = "manifest-check"

class ManagerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        UpdateNotifier.ensureChannel(this)
        scheduleManifestCheck()
    }

    private fun scheduleManifestCheck() {
        val request =
            PeriodicWorkRequestBuilder<ManifestCheckWorker>(MANIFEST_CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        // UPDATE, not REPLACE - re-enqueuing on every process start must not reset an already-scheduled
        // check's timer, but unlike KEEP it does apply a changed interval to work scheduled by an
        // older version of the app (0.1.x scheduled this every 15 minutes)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            MANIFEST_CHECK_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
