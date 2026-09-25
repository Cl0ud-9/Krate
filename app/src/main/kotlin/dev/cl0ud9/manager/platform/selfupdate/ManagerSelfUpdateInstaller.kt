package dev.cl0ud9.manager.platform.selfupdate

import android.content.Context
import dev.cl0ud9.manager.data.downloads.UserFacingIOException
import dev.cl0ud9.manager.data.downloads.friendlyHttpError
import dev.cl0ud9.manager.data.downloads.friendlyNetworkError
import dev.cl0ud9.manager.domain.installer.InstallationEngine
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.InstallStatus
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.model.SupportStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

sealed interface SelfUpdateState {
    // fraction is null until the size is known
    data class Downloading(
        val fraction: Float?,
    ) : SelfUpdateState

    // reuses InstallStatus as-is (Installing/WaitingForUser/Success/Failed) rather than re-modeling
    // it - PackageInstallerEngine.install() only ever emits that subset for a plain UPDATE, the
    // Settings UI already knows how to read the same states App Details does
    data class Installing(
        val installStatus: InstallStatus,
    ) : SelfUpdateState

    data class DownloadFailed(
        val reason: String,
    ) : SelfUpdateState
}

private const val DOWNLOAD_TIMEOUT_SECONDS = 60L
private const val BUFFER_SIZE = 64 * 1024
private const val PROGRESS_STEP_BYTES = 512 * 1024L

private fun defaultHttpClient(): OkHttpClient =
    OkHttpClient
        .Builder()
        .connectTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

// downloads the .apk asset from a manager GitHub Release and installs it over the running app
// through the exact same InstallationEngine every other app already updates through - Android
// refuses to install an update whose signing certificate doesn't match the currently-installed
// one, so this can never succeed against a tampered or wrongly-signed download even without this
// class doing its own signature check first. the system's own install confirmation dialog this
// triggers IS the "prompt to update" - there's no separate custom one to build
class ManagerSelfUpdateInstaller(
    private val context: Context,
    private val installationEngine: InstallationEngine,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {
    fun downloadAndInstall(downloadUrl: String): Flow<SelfUpdateState> =
        flow {
            emit(SelfUpdateState.Downloading(null))
            val apkFile = File(context.cacheDir, "manager-update.apk")
            val failure =
                try {
                    download(downloadUrl, apkFile)
                    null
                } catch (exception: IOException) {
                    friendlyNetworkError(exception)
                }
            if (failure != null) {
                emit(SelfUpdateState.DownloadFailed(failure))
                return@flow
            }
            ManagerUpdatedReceiver.markSelfUpdatePending(context)
            emitAll(installationEngine.install(selfProfile(), apkFile).map { SelfUpdateState.Installing(it) })
        }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<SelfUpdateState>.download(
        url: String,
        destination: File,
    ) {
        httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw UserFacingIOException(friendlyHttpError(response.code))
            }
            val body = response.body ?: throw UserFacingIOException("The update download was empty. Try again.")
            val total = body.contentLength().takeIf { it > 0 }
            destination.outputStream().use { out ->
                body.byteStream().use { input -> copyWithProgress(input, out, total) }
            }
        }
    }

    private suspend fun FlowCollector<SelfUpdateState>.copyWithProgress(
        input: InputStream,
        out: OutputStream,
        total: Long?,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var copied = 0L
        var lastReported = 0L
        var read = input.read(buffer)
        while (read >= 0) {
            out.write(buffer, 0, read)
            copied += read
            if (total != null && copied - lastReported >= PROGRESS_STEP_BYTES) {
                lastReported = copied
                emit(SelfUpdateState.Downloading(copied.toFloat() / total))
            }
            read = input.read(buffer)
        }
    }

    // only packageName is actually read by PackageInstallerEngine.install() - the rest of this
    // profile is never inspected, it exists purely to satisfy the shared InstallationEngine contract
    private fun selfProfile(): AppProfile =
        AppProfile(
            id = "manager-self-update",
            displayName = "Krate",
            packageName = context.packageName,
            supportStatus = SupportStatus.SUPPORTED,
            installationMode = InstallationMode.UPDATE,
            dependencyIds = emptyList(),
            releaseNotes = null,
            enabled = true,
            artifacts = emptyList(),
        )
}
