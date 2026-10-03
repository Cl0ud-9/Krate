package dev.cl0ud9.krate.platform.autoupdate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dev.cl0ud9.krate.data.activity.INSTALL_FAILURE_PREFIX
import dev.cl0ud9.krate.domain.model.ActivityAction
import dev.cl0ud9.krate.domain.model.ActivityEntry
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.repository.buildKey
import dev.cl0ud9.krate.platform.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

// PackageInstaller.STATUS_FAILURE_TIMEOUT (Android 14+): the app stayed in use for the whole wait
private const val STATUS_WAITED_TOO_LONG = 8

internal enum class AutoUpdateOutcome {
    UPDATED,

    // Android wants the user to confirm (the app targets an old SDK, or another store owns it)
    NEEDS_YOU,

    // the app was in use the whole time; tried again on the next check, not counted as a failure
    NOT_NOW,
    TRY_AGAIN,
    GIVE_UP,
}

internal fun autoUpdateOutcome(
    status: Int,
    triesSoFar: Int,
): AutoUpdateOutcome =
    when (status) {
        PackageInstaller.STATUS_SUCCESS -> AutoUpdateOutcome.UPDATED
        PackageInstaller.STATUS_PENDING_USER_ACTION -> AutoUpdateOutcome.NEEDS_YOU
        STATUS_WAITED_TOO_LONG -> AutoUpdateOutcome.NOT_NOW
        // a different signature, a broken file or a build the phone can't run won't get better by trying again
        PackageInstaller.STATUS_FAILURE_CONFLICT,
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
        PackageInstaller.STATUS_FAILURE_INVALID,
        -> AutoUpdateOutcome.GIVE_UP
        else -> if (triesSoFar + 1 >= MAX_AUTO_UPDATE_TRIES) AutoUpdateOutcome.GIVE_UP else AutoUpdateOutcome.TRY_AGAIN
    }

// what a background install came to, recorded the same way an install in the app is
internal object AutoUpdateResults {
    fun handle(
        receiver: BroadcastReceiver,
        context: Context,
        intent: Intent,
    ) {
        val appId = intent.getStringExtra(EXTRA_AUTO_APP_ID) ?: return
        val buildKey = intent.getStringExtra(EXTRA_AUTO_BUILD_KEY) ?: return
        val result =
            Result(
                sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1),
                status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE),
                message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
            )
        val pending = receiver.goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                record(context.applicationContext, appId, buildKey, result)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun record(
        context: Context,
        appId: String,
        buildKey: String,
        result: Result,
    ) {
        val ledger = AutoUpdateLedger(context)
        ledger.landed(appId)
        val container = context.appContainer()
        val app =
            container.catalogRepository
                .observeApps()
                .first()
                .find { it.id == appId } ?: return
        when (autoUpdateOutcome(result.status, ledger.tries(appId, buildKey))) {
            AutoUpdateOutcome.UPDATED -> updated(context, app, buildKey)
            AutoUpdateOutcome.NEEDS_YOU -> {
                runCatching { context.packageManager.packageInstaller.abandonSession(result.sessionId) }
                ledger.giveUp(appId, buildKey)
                AutoUpdateNotices.needsYou(context, app.displayName)
            }
            AutoUpdateOutcome.NOT_NOW -> Unit
            AutoUpdateOutcome.TRY_AGAIN -> ledger.failed(appId, buildKey)
            AutoUpdateOutcome.GIVE_UP -> {
                ledger.giveUp(appId, buildKey)
                val reason = result.message ?: "the automatic update didn't finish."
                log(context, app, ActivityAction.FAILED, "$INSTALL_FAILURE_PREFIX $reason")
                AutoUpdateNotices.needsYou(context, app.displayName)
            }
        }
    }

    private suspend fun updated(
        context: Context,
        app: AppProfile,
        buildKey: String,
    ) {
        val container = context.appContainer()
        val artifact = app.artifacts.find { it.buildKey == buildKey }
        // the user already put this build on by hand while it waited, so there's nothing new to tell
        val recorded = container.krateBaselineStore.observeBaselines().first()[app.packageName]
        if (recorded != null && (recorded.buildId ?: recorded.versionName) == buildKey) return
        if (artifact != null) {
            container.krateBaselineStore.recordInstall(app.packageName, artifact)
            container.artifactDownloader
                .existingReadyFile(app, artifact)
                ?.let { container.artifactDownloader.deleteDownloadedFile(it) }
        }
        container.activityLogRepository.clearFailures(app.id)
        log(context, app, ActivityAction.UPDATED, "Automatically, while you weren't using it")
        AutoUpdateNotices.updated(context, app.displayName)
    }

    private suspend fun log(
        context: Context,
        app: AppProfile,
        action: ActivityAction,
        detail: String,
    ) = context.appContainer().activityLogRepository.record(
        ActivityEntry(
            id = UUID.randomUUID().toString(),
            appId = app.id,
            appName = app.displayName,
            action = action,
            timestampMillis = System.currentTimeMillis(),
            detail = detail,
        ),
    )

    private class Result(
        val sessionId: Int,
        val status: Int,
        val message: String?,
    )
}
