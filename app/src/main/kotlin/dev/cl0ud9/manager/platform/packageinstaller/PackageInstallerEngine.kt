package dev.cl0ud9.manager.platform.packageinstaller

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dev.cl0ud9.manager.domain.installer.InstallationEngine
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.InstallStatus
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.io.File

// drives an android PackageInstaller session for install/update and uninstall, section 15, 16, 17,
// 42.12 of the spec - the clean-install sequence (uninstall then install, with rollback) lives one
// layer up in CleanInstallOrchestrator, this class only knows how to run each individual step
class PackageInstallerEngine(
    private val context: Context,
) : InstallationEngine {
    override fun install(
        app: AppProfile,
        apkFile: File,
    ): Flow<InstallStatus> =
        callbackFlow {
            send(InstallStatus.Installing)
            val requestKey = "install:${app.packageName}"
            val started = runCatching { createAndCommitSession(app, apkFile, requestKey) }
            if (started.isFailure) {
                val reason = started.exceptionOrNull()?.message ?: "unknown error"
                send(InstallStatus.Failed("Could not start installation: $reason"))
                close()
                return@callbackFlow
            }
            awaitResult(requestKey)
        }

    override fun uninstall(packageName: String): Flow<InstallStatus> =
        callbackFlow {
            val requestKey = "uninstall:$packageName"
            val started =
                runCatching {
                    context.packageManager.packageInstaller
                        .uninstall(packageName, pendingIntentFor(requestKey).intentSender)
                }
            if (started.isFailure) {
                val reason = started.exceptionOrNull()?.message ?: "unknown error"
                send(InstallStatus.Failed("Could not start uninstall: $reason"))
                close()
                return@callbackFlow
            }
            awaitResult(requestKey)
        }

    // collects broadcast results for the given request key onto this producer until a terminal state arrives -
    // callbackFlow requires awaitClose to be the block's last suspending call, so this must stay suspend
    private suspend fun ProducerScope<InstallStatus>.awaitResult(requestKey: String) {
        val job =
            launch {
                InstallResultBus.events
                    .filter { it.requestKey == requestKey }
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
        requestKey: String,
    ) {
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
            session.commit(pendingIntentFor(requestKey).intentSender)
        }
    }

    private fun pendingIntentFor(requestKey: String): PendingIntent {
        val intent = Intent(context, InstallResultReceiver::class.java).putExtra(EXTRA_REQUEST_KEY, requestKey)
        return PendingIntent.getBroadcast(context, requestKey.hashCode(), intent, installResultPendingIntentFlags())
    }
}
