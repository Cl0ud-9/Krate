package dev.cl0ud9.krate.platform.packageinstaller

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.cl0ud9.krate.domain.installer.InstallationEngine
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.domain.model.WaitingForUserStep
import dev.cl0ud9.krate.platform.lifecycle.ActivityFocus
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

private const val PROMPT_SETTLE_MS = 1_500L

// how long Krate may stay in front after an uninstall prompt was asked for before it's taken the prompt never showed
private const val PROMPT_APPEAR_MS = 6_000L

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
                // plain words, never the internal file path an exception message carries
                val reason =
                    if (started.exceptionOrNull() is java.io.IOException) {
                        "Couldn't read the downloaded file. Download it again and try once more."
                    } else {
                        "Couldn't start the install. Try again."
                    }
                send(InstallStatus.Failed(reason))
                close()
                return@callbackFlow
            }
            awaitResult(requestKey, WaitingForUserStep.INSTALL_CONFIRM)
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
                send(InstallStatus.Failed("Couldn't start the uninstall: $reason"))
                close()
                return@callbackFlow
            }
            awaitResult(requestKey, WaitingForUserStep.UNINSTALL_CONFIRM, packageName)
        }

    // collects broadcast results for the given request key onto this producer until a terminal state arrives -
    // callbackFlow requires awaitClose to be the block's last suspending call, so this must stay suspend.
    // [uninstalling] is the package an uninstall is for: Android reports nothing when someone says no to its
    // confirmation, so for one the wait also ends when the dialog has plainly been closed
    private suspend fun ProducerScope<InstallStatus>.awaitResult(
        requestKey: String,
        waitingForUserStep: WaitingForUserStep,
        uninstalling: String? = null,
    ) {
        var closedWatch: Job? = null
        val job =
            launch {
                InstallResultBus.events
                    .filter { it.requestKey == requestKey }
                    .collect { event ->
                        val status = interpretInstallResult(event.status, event.message, waitingForUserStep)
                        send(status)
                        if (status is InstallStatus.WaitingForUser && uninstalling != null) {
                            closedWatch?.cancel()
                            closedWatch = launch { endWaitWhenPromptClosed(uninstalling) }
                        }
                        if (status is InstallStatus.Success || status is InstallStatus.Failed) {
                            closedWatch?.cancel()
                            close()
                        }
                    }
            }
        awaitClose {
            closedWatch?.cancel()
            job.cancel()
        }
    }

    // the dialog sits over Krate, which stops being the one in front, and closing it brings Krate back. If the app is
    // still on the phone a moment after that, nobody confirmed it, so the wait ends as a cancel rather than hanging on
    // a spinner. Screen focus is read per screen, so a dialog cancelled the instant it opens is still seen: and if
    // Krate never lost the front at all, the dialog never came up (or is long gone), which ends the wait the same way
    private suspend fun ProducerScope<InstallStatus>.endWaitWhenPromptClosed(packageName: String) {
        val focus = ActivityFocus.inFront
        if (focus.value) withTimeoutOrNull(PROMPT_APPEAR_MS) { focus.first { !it } }
        focus.first { it }
        // a confirmed uninstall reports within a moment of the dialog closing, so give that time to arrive first
        delay(PROMPT_SETTLE_MS)
        if (isStillInstalled(packageName)) {
            PendingConfirmation.clear()
            send(InstallStatus.Failed("Uninstall cancelled.", userCancelled = true, mayBeAppLock = true))
            close()
        }
    }

    @Suppress("DEPRECATION")
    private fun isStillInstalled(packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    private fun createAndCommitSession(
        app: AppProfile,
        apkFile: File,
        requestKey: String,
    ) {
        val packageInstaller = context.packageManager.packageInstaller
        val sessionId = packageInstaller.createSession(krateSessionParams(app.packageName, apkFile))
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
