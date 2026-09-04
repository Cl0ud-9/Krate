package dev.cl0ud9.manager.data.downloads

import android.os.StatFs
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.ArtifactInfo
import dev.cl0ud9.manager.domain.model.DownloadStatus
import dev.cl0ud9.manager.security.apk.ApkArchiveReader
import dev.cl0ud9.manager.security.hash.hashesMatch
import dev.cl0ud9.manager.security.hash.sha256Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

private const val STREAM_BUFFER_SIZE = 8192
private const val PROGRESS_EMIT_INTERVAL_BYTES = 256 * 1024L
private const val HTTP_PARTIAL_CONTENT = 206

// safety margin over the artifact size to leave room for the rollback copy and install staging, section 42.6
private const val STORAGE_SAFETY_MARGIN = 1.5
private const val BYTES_PER_MB = 1024L * 1024L
private const val DEFAULT_MIN_FREE_BYTES = 50L * BYTES_PER_MB

// streams the artifact to a resumable .part file, then verifies hash + certificate + package name before
// handing back a ready-to-install path, section 19, 42.5, 42.6, 42.9 of the spec
class OkHttpArtifactDownloader(
    private val downloadsDir: File,
    private val archiveReader: ApkArchiveReader,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : ArtifactDownloader {
    override fun download(app: AppProfile): Flow<DownloadStatus> =
        flow {
            val artifact = app.artifact
            if (artifact == null) {
                emit(DownloadStatus.Failed("No downloadable artifact is available for this app yet."))
                return@flow
            }

            downloadsDir.mkdirs()
            val partFile = File(downloadsDir, "${app.id}.apk.part")
            val readyFile = File(downloadsDir, "${app.id}.apk")

            val preflightFailure = checkStoragePreflight(artifact)
            if (preflightFailure != null) {
                emit(DownloadStatus.Failed(preflightFailure))
                return@flow
            }

            val downloadFailure = runDownload(artifact, partFile, this)
            if (downloadFailure != null) {
                emit(DownloadStatus.Failed(downloadFailure))
                return@flow
            }

            emit(DownloadStatus.Verifying)
            val verificationFailure = verify(artifact, app.packageName, partFile)
            if (verificationFailure != null) {
                partFile.delete()
                emit(DownloadStatus.Failed(verificationFailure))
                return@flow
            }

            partFile.copyTo(readyFile, overwrite = true)
            partFile.delete()
            emit(DownloadStatus.ReadyToInstall(readyFile.absolutePath))
        }.flowOn(Dispatchers.IO)

    // returns a user-facing failure message, or null on success
    private suspend fun runDownload(
        artifact: ArtifactInfo,
        partFile: File,
        collector: FlowCollector<DownloadStatus>,
    ): String? =
        runCatching { streamDownload(artifact, partFile, collector) }
            .fold(onSuccess = { null }, onFailure = { "Download failed: ${it.message ?: "network error"}" })

    private fun checkStoragePreflight(artifact: ArtifactInfo): String? {
        val contentLength = headContentLength(artifact.downloadUrl)
        val required = ((contentLength ?: DEFAULT_MIN_FREE_BYTES) * STORAGE_SAFETY_MARGIN).toLong()
        val available = runCatching { StatFs(downloadsDir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (available < required) {
            val requiredMb = required / BYTES_PER_MB
            val availableMb = available / BYTES_PER_MB
            return "Not enough storage: need about ${requiredMb}MB, ${availableMb}MB available."
        }
        return null
    }

    private fun headContentLength(url: String): Long? =
        runCatching {
            httpClient
                .newCall(
                    Request
                        .Builder()
                        .url(url)
                        .head()
                        .build(),
                ).execute()
                .use { response ->
                    response.header("Content-Length")?.toLongOrNull()
                }
        }.getOrNull()

    // streams the response body to the part file, resuming from its existing length when the server allows it
    private suspend fun streamDownload(
        artifact: ArtifactInfo,
        partFile: File,
        collector: FlowCollector<DownloadStatus>,
    ) {
        val existingBytes = if (partFile.exists()) partFile.length() else 0L
        val requestBuilder = Request.Builder().url(artifact.downloadUrl)
        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }
        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) error("Server returned HTTP ${response.code}")
            val resuming = response.code == HTTP_PARTIAL_CONTENT
            val body = response.body ?: error("Empty response body")
            val totalBytes = resolveTotalBytes(response, resuming, existingBytes, body.contentLength())
            val startingAt = if (resuming) existingBytes else 0L
            writeBody(body.byteStream(), partFile, append = resuming, startingAt = startingAt) { written ->
                collector.emit(DownloadStatus.Downloading(bytesDownloaded = written, totalBytes = totalBytes))
            }
        }
    }

    private fun resolveTotalBytes(
        response: Response,
        resuming: Boolean,
        existingBytes: Long,
        bodyContentLength: Long,
    ): Long {
        if (resuming) {
            val total = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
            return total ?: (existingBytes + bodyContentLength)
        }
        return bodyContentLength
    }

    private suspend fun writeBody(
        input: InputStream,
        partFile: File,
        append: Boolean,
        startingAt: Long,
        onProgress: suspend (Long) -> Unit,
    ) {
        input.use { stream ->
            FileOutputStream(partFile, append).use { output ->
                copyWithProgress(stream, output, startingAt, onProgress)
            }
        }
    }

    private suspend fun copyWithProgress(
        input: InputStream,
        output: FileOutputStream,
        startingAt: Long,
        onProgress: suspend (Long) -> Unit,
    ) {
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        var written = startingAt
        var sinceLastEmit = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            written += read
            sinceLastEmit += read
            if (sinceLastEmit >= PROGRESS_EMIT_INTERVAL_BYTES) {
                sinceLastEmit = 0L
                onProgress(written)
            }
        }
        onProgress(written)
    }

    private fun verify(
        artifact: ArtifactInfo,
        expectedPackageName: String,
        file: File,
    ): String? {
        val hashMatches = hashesMatch(artifact.sha256, sha256Hex(file))
        val archiveInfo = archiveReader.read(file.absolutePath)
        return when {
            !hashMatches -> {
                "Downloaded file does not match the expected checksum."
            }

            archiveInfo == null -> {
                "Downloaded file is not a valid APK."
            }

            archiveInfo.packageName != expectedPackageName -> {
                "Downloaded package name does not match the catalog entry."
            }

            !hashesMatch(artifact.certificateSha256, archiveInfo.certificateSha256Hex) -> {
                "Signing certificate does not match the expected identity."
            }

            else -> {
                null
            }
        }
    }
}
