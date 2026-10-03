package dev.cl0ud9.krate.platform.autoupdate

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.os.storage.StorageManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.cl0ud9.krate.data.downloads.ArtifactDownloader
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.latestArtifact
import dev.cl0ud9.krate.domain.repository.buildKey
import dev.cl0ud9.krate.platform.appContainer
import dev.cl0ud9.krate.platform.workers.pendingUpdates
import kotlinx.coroutines.flow.first
import java.io.File

private const val AUTO_UPDATE_WORK_NAME = "auto-update"

// room for the session's copy of the apk and the installed result, with some to spare
private const val SPACE_FACTOR = 3

// installs the updates that are due, from files the background check already downloaded; it never downloads,
// so the Wi-Fi and mobile data rules stay with the check
class AutoUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val ledger = AutoUpdateLedger(applicationContext)
    private val installer = BackgroundInstaller(applicationContext)

    override suspend fun doWork(): Result {
        // never retried from here: the next background check queues it again, so a failure can't loop
        if (autoUpdatesOn(applicationContext) && phoneIsFree()) {
            runCatching { installDue() }
        }
        return Result.success()
    }

    private suspend fun installDue() {
        val container = applicationContext.appContainer()
        val apps = container.catalogRepository.observeApps().first()
        val pending =
            pendingUpdates(
                apps,
                container.installedPackageReader,
                container.githubCredentialStore.getToken() != null,
                container.krateBaselineStore.observeBaselines().first(),
            )
        val now = System.currentTimeMillis()
        val policy = autoUpdatePolicy(applicationContext, apps)
        policy.inInstallOrder(pending.filter { policy.isDue(it, now) }).forEach { app ->
            handOver(app, container.artifactDownloader, now)
        }
    }

    // only a file the background check already downloaded and verified, and only once at a time
    private fun handOver(
        app: AppProfile,
        downloader: ArtifactDownloader,
        now: Long,
    ) {
        val artifact = app.latestArtifact ?: return
        val key = artifact.buildKey
        val file = downloader.existingReadyFile(app, artifact)?.let(::File)?.takeIf(::hasRoomFor)
        if (file != null && !ledger.isInFlight(app.id, key, now)) {
            if (installer.handOver(app, key, file)) ledger.handedOver(app.id, key, now) else ledger.failed(app.id, key)
        }
    }

    // Android 14+ waits for each app to be out of use by itself; before that the phone must be idle, screen off,
    // with nothing playing (a music app mid-song would stop)
    private fun phoneIsFree(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val power = applicationContext.getSystemService(PowerManager::class.java)
        val audio = applicationContext.getSystemService(AudioManager::class.java)
        return power?.isInteractive == false && audio?.isMusicActive == false
    }

    // counts cache Android can clear to make room, not only what's free right now
    private fun hasRoomFor(file: File): Boolean =
        runCatching {
            val storage = applicationContext.getSystemService(StorageManager::class.java)
            storage.getAllocatableBytes(storage.getUuidForPath(applicationContext.dataDir)) >
                file.length() * SPACE_FACTOR
        }.getOrDefault(false)

    companion object {
        // waits for a charged-enough battery and free storage; before Android 14 also for the phone to sit idle
        fun enqueue(context: Context) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .apply {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) setRequiresDeviceIdle(true)
                    }.build()
            val request = OneTimeWorkRequestBuilder<AutoUpdateWorker>().setConstraints(constraints).build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(AUTO_UPDATE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
