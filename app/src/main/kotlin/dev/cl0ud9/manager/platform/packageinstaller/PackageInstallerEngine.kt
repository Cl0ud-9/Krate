package dev.cl0ud9.manager.platform.packageinstaller

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dev.cl0ud9.manager.domain.installer.InstallationEngine
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.InstallStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.io.File

// drives an android PackageInstaller session for the normal UPDATE flow, section 15, 16, 42.12 of the spec
// clean-install (uninstall + install, used by videoapp patched) is phase 5, not handled here
class PackageInstallerEngine(
    private val context: Context,
) : InstallationEngine {
    override fun install(
        app: AppProfile,
        apkFile: File,
    ): Flow<InstallStatus> =
        callbackFlow {
            send(InstallStatus.Installing)
            val sessionId =
                runCatching { createAndCommitSession(app, apkFile) }
                    .getOrElse {
                        send(InstallStatus.Failed("Could not start installation: ${it.message ?: "unknown error"}"))
                        close()
                        return@callbackFlow
                    }

            val job =
                launch {
                    InstallResultBus.events
                        .filter { it.sessionId == sessionId }
                        .collect { event ->
                            val status = interpretInstallResult(event.status, event.message)
                            send(status)
                            if (status is InstallStatus.Success || status is InstallStatus.Failed) {
                                close()
                            }
                        }
                }
            awaitClose { job.cancel() }
        }

    private fun createAndCommitSession(
        app: AppProfile,
        apkFile: File,
    ): Int {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(app.packageName)
        params.setSize(apkFile.length())
        val sessionId = packageInstaller.createSession(params)
        packageInstaller.openSession(sessionId).use { session ->
            session.openWrite("apk", 0, apkFile.length()).use { out ->
                apkFile.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            session.commit(pendingIntentFor(sessionId).intentSender)
        }
        return sessionId
    }

    private fun pendingIntentFor(sessionId: Int): PendingIntent {
        val intent = Intent(context, InstallResultReceiver::class.java)
        return PendingIntent.getBroadcast(context, sessionId, intent, installResultPendingIntentFlags())
    }
}
