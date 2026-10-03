package dev.cl0ud9.krate.platform.autoupdate

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.InstallConstraints
import android.os.Build
import androidx.annotation.RequiresApi
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.platform.packageinstaller.EXTRA_REQUEST_KEY
import dev.cl0ud9.krate.platform.packageinstaller.InstallResultReceiver
import dev.cl0ud9.krate.platform.packageinstaller.installResultPendingIntentFlags
import dev.cl0ud9.krate.platform.packageinstaller.krateSessionParams
import java.io.File

// request keys of installs nobody is watching; their results go to AutoUpdateResults, never to a prompt
internal const val AUTO_UPDATE_REQUEST_PREFIX = "auto:"
internal const val EXTRA_AUTO_APP_ID = "dev.cl0ud9.krate.EXTRA_AUTO_APP_ID"
internal const val EXTRA_AUTO_BUILD_KEY = "dev.cl0ud9.krate.EXTRA_AUTO_BUILD_KEY"

// hands a downloaded update to Android without anyone watching: on Android 14+ it goes on once the app is out of
// use, earlier it goes on now (the worker only runs while the phone sits idle)
internal class BackgroundInstaller(
    private val context: Context,
) {
    fun handOver(
        app: AppProfile,
        buildKey: String,
        apkFile: File,
    ): Boolean {
        val installer = context.packageManager.packageInstaller
        val sessionId = runCatching { installer.createSession(krateSessionParams(app.packageName, apkFile)) }
        return sessionId
            .mapCatching { id -> commit(installer, id, app, buildKey, apkFile) }
            .onFailure { sessionId.onSuccess { id -> runCatching { installer.abandonSession(id) } } }
            .isSuccess
    }

    private fun commit(
        installer: PackageInstaller,
        sessionId: Int,
        app: AppProfile,
        buildKey: String,
        apkFile: File,
    ) {
        installer.openSession(sessionId).use { session ->
            session.openWrite("apk", 0, apkFile.length()).use { out ->
                apkFile.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                session.commit(resultSender(app, buildKey))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            commitWhenFree(installer, sessionId, app, buildKey)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun commitWhenFree(
        installer: PackageInstaller,
        sessionId: Int,
        app: AppProfile,
        buildKey: String,
    ) {
        // not on screen and not in use (playing, or bound by what is on screen)
        val constraints =
            InstallConstraints
                .Builder()
                .setAppNotTopVisibleRequired()
                .setAppNotInteractingRequired()
                .build()
        installer.commitSessionAfterInstallConstraintsAreMet(
            sessionId,
            resultSender(app, buildKey),
            constraints,
            AUTO_UPDATE_WAIT_MILLIS,
        )
    }

    private fun resultSender(
        app: AppProfile,
        buildKey: String,
    ) = PendingIntent
        .getBroadcast(
            context,
            "$AUTO_UPDATE_REQUEST_PREFIX${app.id}".hashCode(),
            Intent(context, InstallResultReceiver::class.java)
                .putExtra(EXTRA_REQUEST_KEY, "$AUTO_UPDATE_REQUEST_PREFIX${app.packageName}")
                .putExtra(EXTRA_AUTO_APP_ID, app.id)
                .putExtra(EXTRA_AUTO_BUILD_KEY, buildKey),
            installResultPendingIntentFlags(),
        ).intentSender
}
