package dev.cl0ud9.krate.platform.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.cl0ud9.krate.platform.selfupdate.APP_GIVEN_UP_AFTER_MILLIS
import dev.cl0ud9.krate.platform.selfupdate.PLAY_PROTECT_ROUTE
import dev.cl0ud9.krate.platform.selfupdate.PlayProtect
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "play-protect-reminder"
private const val REMINDER_NOTIFICATION_ID = 1008

// past the moment the reminder falls due, so the check never lands just short of it
private const val MARGIN_MILLIS = 60_000L

// a little while after Play Protect was paused for an app: if no one has said it's back on, a notification asks. The
// cards in the app ask too, but only while Krate is open
class PlayProtectReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        when {
            !PlayProtect.reminderPending(context) -> Unit
            PlayProtect.reminderDue(context) -> notifyReminder(context, PlayProtect.pausedForApp(context))
            // run a touch early (clocks drift): come back once it's due rather than drop it
            else -> schedule(context)
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<PlayProtectReminderWorker>()
                    .setInitialDelay(APP_GIVEN_UP_AFTER_MILLIS + MARGIN_MILLIS, TimeUnit.MILLISECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

// the tap opens Play Protect itself, and settles the reminder
private fun notifyReminder(
    context: Context,
    appName: String?,
) {
    UpdateNotifier.notify(
        context,
        REMINDER_NOTIFICATION_ID,
        KrateNotification(
            headline = "Turn Play Protect back on",
            fact =
                "You paused Play Protect${appName?.let { " to install $it" }.orEmpty()}. Turning it back on keeps it " +
                    "checking the apps you install.",
            category = "Play Protect",
            targetRoute = PLAY_PROTECT_ROUTE,
            actionLabel = "Open Play Protect",
        ),
    )
}
