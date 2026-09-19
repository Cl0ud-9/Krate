package dev.cl0ud9.manager.platform.selfupdate

import android.content.Context
import dev.cl0ud9.manager.domain.installer.InstallationEngine
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.InstallStatus
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.model.SupportStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

sealed interface SelfUpdateState {
    data object Downloading : SelfUpdateState

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
            emit(SelfUpdateState.Downloading)
            val apkFile = File(context.cacheDir, "manager-update.apk")
            val downloadResult = withContext(Dispatchers.IO) { runCatching { download(downloadUrl, apkFile) } }
            if (downloadResult.isFailure) {
                emit(SelfUpdateState.DownloadFailed(downloadResult.exceptionOrNull()?.message ?: "Download failed."))
                return@flow
            }
            emitAll(installationEngine.install(selfProfile(), apkFile).map { SelfUpdateState.Installing(it) })
        }

    private fun download(
        url: String,
        destination: File,
    ) {
        httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "GitHub returned ${response.code}." }
            val body = response.body ?: error("Empty response.")
            destination.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
    }

    // only packageName is actually read by PackageInstallerEngine.install() - the rest of this
    // profile is never inspected, it exists purely to satisfy the shared InstallationEngine contract
    private fun selfProfile(): AppProfile =
        AppProfile(
            id = "manager-self-update",
            displayName = "App Manager",
            packageName = context.packageName,
            supportStatus = SupportStatus.SUPPORTED,
            installationMode = InstallationMode.UPDATE,
            dependencyIds = emptyList(),
            releaseNotes = null,
            enabled = true,
            artifacts = emptyList(),
        )
}
