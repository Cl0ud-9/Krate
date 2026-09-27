package dev.cl0ud9.krate

import android.app.Application
import androidx.compose.ui.AndroidComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.cl0ud9.krate.platform.AppContainer
import dev.cl0ud9.krate.platform.LauncherIcon
import dev.cl0ud9.krate.platform.workers.ManifestCheckWorker
import dev.cl0ud9.krate.platform.workers.UpdateNotifier
import dev.cl0ud9.krate.voice.KrateVoice
import java.util.concurrent.TimeUnit

// section 40 of the spec: WorkManager periodic work is intentionally inexact. Every 6 hours is plenty -
// the catalog itself is regenerated every 2 hours and privately built apps land at most twice a day, and a
// pull-to-refresh in the app always checks right away. Stands on its own since FCM needs a Firebase
// project this app can't set up for itself
private const val MANIFEST_CHECK_INTERVAL_HOURS = 6L
private const val MANIFEST_CHECK_WORK_NAME = "manifest-check"

class KrateApplication : Application() {
    lateinit var container: AppContainer
        private set

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate() {
        super.onCreate()
        // frame-based text input scheduling, so moving between text fields doesn't drop and re-show the keyboard
        AndroidComposeUiFlags.isOutOfFrameSchedulerForTextInputEventsEnabled = false
        container = AppContainer(this)
        LauncherIcon.showUnlessHidden(this)
        KrateVoice.load(this)
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
