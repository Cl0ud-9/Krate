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

// section 40 of the spec: WorkManager periodic work is intentionally inexact, minimum interval 15
// minutes - this is a fallback for missed FCM notifications (section 9/24/44.4), and stands on its
// own since FCM needs a Firebase project this app can't set up for itself
private const val MANIFEST_CHECK_INTERVAL_MINUTES = 15L
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
            PeriodicWorkRequestBuilder<ManifestCheckWorker>(MANIFEST_CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        // KEEP, not REPLACE - re-enqueuing on every process start must not reset an already-scheduled
        // check's timer, or it would never actually fire on its intended cadence
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            MANIFEST_CHECK_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
