package dev.cl0ud9.krate.platform.selfupdate

import android.content.Context
import dev.cl0ud9.krate.data.downloads.UserFacingIOException
import dev.cl0ud9.krate.data.downloads.friendlyHttpError
import dev.cl0ud9.krate.data.downloads.friendlyNetworkError
import dev.cl0ud9.krate.domain.installer.InstallationEngine
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.model.SupportStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

// downloads the .apk asset from a Krate GitHub release and installs it over the running app
// through the exact same InstallationEngine every other app already updates through - Android
// refuses to install an update whose signing certificate doesn't match the currently-installed
// one, so this can never succeed against a tampered or wrongly-signed download even without this
// class doing its own signature check first. the system's own install confirmation dialog this
// triggers IS the "prompt to update" - there's no separate custom one to build
class KrateSelfUpdateInstaller internal constructor(
    private val cacheDir: File,
    private val packageName: String,
    // called right before Android's installer takes over and closes Krate
    private val beforeInstall: () -> Unit,
    private val installationEngine: InstallationEngine,
    private val httpClient: OkHttpClient,
) {
    constructor(context: Context, installationEngine: InstallationEngine) : this(
        cacheDir = context.cacheDir,
        packageName = context.packageName,
        beforeInstall = { KrateUpdatedReceiver.markSelfUpdatePending(context) },
        installationEngine = installationEngine,
        httpClient = defaultHttpClient(),
    )

    // one update for all of Krate: Home and Settings show the same one, and leaving the screen it was started from
    // doesn't stop it
    private val mutableState = MutableStateFlow<SelfUpdateState?>(null)
    val state: StateFlow<SelfUpdateState?> = mutableState.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    // a tap while one is under way does nothing; once it has stopped (Play Protect, a failed download) it starts again
    @Synchronized
    fun start(downloadUrl: String) {
        if (running?.isActive == true) return
        running = scope.launch { downloadAndInstall(downloadUrl).collect { mutableState.value = it } }
    }

    // Settings' Clear cache: a downloaded update nobody installed, unless it's being installed right now
    @Synchronized
    fun clearDownload(): Long = if (running?.isActive == true) 0L else forgetSelfUpdateDownload(cacheDir)

    // waits for the update under way, if any, to stop
    internal suspend fun join() {
        running?.join()
    }

    private fun downloadAndInstall(downloadUrl: String): Flow<SelfUpdateState> =
        flow {
            val apkFile = selfUpdateApk(cacheDir)
            // a second go at the same release (after pausing Play Protect, say) installs what's already here
            if (!isDownloaded(downloadUrl)) {
                // an empty bar to start with; it only turns to a "size unknown" wave if the server never says how big
                emit(SelfUpdateState.Downloading(0f))
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
            }
            beforeInstall()
            emitAll(
                installationEngine.install(selfProfile(), apkFile).map { status ->
                    // a file Android turned down for any other reason is fetched again on the next try
                    if (status is InstallStatus.Failed && !status.blockedByPlayProtect && !status.userCancelled) {
                        forgetSelfUpdateDownload(cacheDir)
                    }
                    SelfUpdateState.Installing(status)
                },
            )
        }.flowOn(Dispatchers.IO)

    private fun isDownloaded(url: String): Boolean {
        val source = selfUpdateSource(cacheDir)
        return selfUpdateApk(cacheDir).isFile && source.isFile && source.readText() == url
    }

    // downloads beside the update and moves it into place only once it's whole, so a cut-off download is never
    // taken for a finished one
    private suspend fun FlowCollector<SelfUpdateState>.download(
        url: String,
        destination: File,
    ) {
        forgetSelfUpdateDownload(cacheDir)
        val partial = File(destination.parentFile, destination.name + PARTIAL_SUFFIX)
        httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw UserFacingIOException(friendlyHttpError(response.code))
            }
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 }
            if (total == null) emit(SelfUpdateState.Downloading(null))
            partial.outputStream().use { out ->
                body.byteStream().use { input -> copyWithProgress(input, out, total) }
            }
        }
        if (!partial.renameTo(destination)) {
            partial.delete()
            throw IOException("couldn't keep the downloaded update")
        }
        selfUpdateSource(cacheDir).writeText(url)
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
            id = "krate-self-update",
            displayName = "Krate",
            packageName = packageName,
            supportStatus = SupportStatus.SUPPORTED,
            installationMode = InstallationMode.UPDATE,
            dependencyIds = emptyList(),
            releaseNotes = null,
            enabled = true,
            artifacts = emptyList(),
        )
}

private const val PARTIAL_SUFFIX = ".part"

// where Krate's own update is downloaded to; deleted once that update is installed (KrateUpdatedReceiver)
internal fun selfUpdateApk(cacheDir: File): File = File(cacheDir, "krate-update.apk")

// the release the downloaded update came from, written once the download is whole
private fun selfUpdateSource(cacheDir: File): File = File(cacheDir, "krate-update.source")

// the downloaded update and anything left of a cut-off one; how many bytes that frees
internal fun forgetSelfUpdateDownload(cacheDir: File): Long {
    selfUpdateSource(cacheDir).delete()
    val apk = selfUpdateApk(cacheDir)
    return listOf(apk, File(cacheDir, apk.name + PARTIAL_SUFFIX)).sumOf { file ->
        file.length().also { file.delete() }
    }
}
