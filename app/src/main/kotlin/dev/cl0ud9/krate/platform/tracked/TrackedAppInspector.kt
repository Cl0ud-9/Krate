package dev.cl0ud9.krate.platform.tracked

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Base64
import androidx.core.graphics.drawable.toBitmap
import dev.cl0ud9.krate.data.tracked.ForgeAnswer
import dev.cl0ud9.krate.data.tracked.ForgeReleases
import dev.cl0ud9.krate.data.tracked.Release
import dev.cl0ud9.krate.domain.tracked.ReleaseAsset
import dev.cl0ud9.krate.domain.tracked.RepoRef
import dev.cl0ud9.krate.domain.tracked.apkCandidates
import dev.cl0ud9.krate.security.apk.identitySha256
import dev.cl0ud9.krate.security.hash.sha256Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

private const val ICON_SIZE_PX = 192
private const val PNG_QUALITY = 100
private const val BUFFER = 64 * 1024
private const val PROGRESS_EVERY_BYTES = 256 * 1024L

// the newest release with an app file for this phone, and everything Krate needs to choose from it
sealed interface LookUp {
    data class Found(
        val ref: RepoRef,
        val description: String?,
        // newest first; the one to add is the newest that has a usable file
        val releases: List<Release>,
        val release: Release,
        val candidates: List<ReleaseAsset>,
        val etag: String?,
    ) : LookUp

    // a real repository, but no release with an app file this phone can run
    data object NoAppFiles : LookUp

    data object NotFound : LookUp

    data class OutOfChecks(
        val withToken: Boolean,
    ) : LookUp

    data object Offline : LookUp
}

// what's inside the downloaded file: who it is, what it's called, and who signed it
data class InspectedApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val iconPng: String?,
    val certificateSha256: String,
    val file: File,
)

sealed interface Inspection {
    data class Downloading(
        val bytes: Long,
        val totalBytes: Long?,
    ) : Inspection

    data object Reading : Inspection

    data class Ready(
        val app: InspectedApp,
    ) : Inspection

    data class Failed(
        val reason: String,
    ) : Inspection
}

// looks a repository up and opens the app file chosen from it, so Krate knows the app before anyone installs it
class TrackedAppInspector(
    private val context: Context,
    private val releases: ForgeReleases,
    private val httpClient: OkHttpClient,
    private val workDir: File,
    // the processor families this phone runs, best first
    val supportedAbis: List<String>,
) {
    suspend fun lookUp(ref: RepoRef): LookUp =
        withContext(Dispatchers.IO) {
            when (val listed = releases.releases(ref, etag = null)) {
                is ForgeAnswer.Found -> {
                    val stable = listed.value.filterNot { it.prerelease }.ifEmpty { listed.value }
                    val release = stable.firstOrNull { apkCandidates(it.assets, supportedAbis).isNotEmpty() }
                    if (release == null) {
                        LookUp.NoAppFiles
                    } else {
                        val description = (releases.repo(ref) as? ForgeAnswer.Found)?.value?.description
                        LookUp.Found(
                            ref = ref,
                            description = description?.takeIf { it.isNotBlank() },
                            releases = listed.value,
                            release = release,
                            candidates = apkCandidates(release.assets, supportedAbis),
                            etag = listed.etag,
                        )
                    }
                }
                is ForgeAnswer.OutOfChecks -> LookUp.OutOfChecks(listed.withToken)
                ForgeAnswer.NotFound -> LookUp.NotFound
                ForgeAnswer.Unchanged, ForgeAnswer.Unreachable -> LookUp.Offline
            }
        }

    // downloads the file into Krate's own storage and reads it; the file stays for installing, or for discard()
    fun inspect(asset: ReleaseAsset): Flow<Inspection> =
        flow {
            workDir.mkdirs()
            val file = File(workDir, "inspect-${UUID.randomUUID()}.apk")
            val failure =
                try {
                    download(asset, file)
                    null
                } catch (exception: IOException) {
                    exception.message ?: "The download stopped. Check your connection and try again."
                }
            if (failure != null) {
                file.delete()
                emit(Inspection.Failed(failure))
                return@flow
            }
            emit(Inspection.Reading)
            val expected = asset.sha256
            if (expected != null && !expected.equals(sha256Hex(file), ignoreCase = true)) {
                file.delete()
                emit(Inspection.Failed("The file was damaged or changed on the way. Try again."))
                return@flow
            }
            val app = read(file)
            if (app == null) {
                file.delete()
                emit(Inspection.Failed("That file isn't an Android app Krate can read."))
            } else {
                emit(Inspection.Ready(app))
            }
        }.flowOn(Dispatchers.IO)

    fun discard(app: InspectedApp) {
        app.file.delete()
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<Inspection>.download(
        asset: ReleaseAsset,
        file: File,
    ) {
        val request = Request.Builder().url(asset.downloadUrl).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(
                    "GitHub didn't send the file (error ${response.code}). Try again.",
                )
            }
            val body = response.body ?: throw IOException("GitHub sent an empty file. Try again.")
            val total = body.contentLength().takeIf { it > 0 } ?: asset.sizeBytes.takeIf { it > 0 }
            emit(Inspection.Downloading(0L, total))
            file.outputStream().use { out -> body.byteStream().use { input -> copy(input, out, total) } }
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<Inspection>.copy(
        input: java.io.InputStream,
        out: java.io.OutputStream,
        total: Long?,
    ) {
        val buffer = ByteArray(BUFFER)
        var written = 0L
        var reported = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            written += read
            if (written - reported >= PROGRESS_EVERY_BYTES) {
                reported = written
                emit(Inspection.Downloading(written, total))
            }
        }
    }

    private fun read(file: File): InspectedApp? {
        val manager = context.packageManager
        val info =
            runCatching { manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES) }
                .getOrNull()
        val certificate = info?.signingInfo?.identitySha256()
        if (info == null || certificate == null) return null
        val application = info.applicationInfo
        // an archive's label and icon load from its own path
        application?.sourceDir = file.absolutePath
        application?.publicSourceDir = file.absolutePath
        val label = application?.let { runCatching { manager.getApplicationLabel(it).toString() }.getOrNull() }
        val icon = application?.let { runCatching { manager.getApplicationIcon(it) }.getOrNull() }
        return InspectedApp(
            packageName = info.packageName,
            label = label?.takeIf { it.isNotBlank() } ?: info.packageName,
            versionName = info.versionName,
            iconPng = icon?.let { runCatching { encodePng(it.toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)) }.getOrNull() },
            certificateSha256 = certificate,
            file = file,
        )
    }

    private fun encodePng(bitmap: Bitmap): String {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, bytes)
        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }
}
