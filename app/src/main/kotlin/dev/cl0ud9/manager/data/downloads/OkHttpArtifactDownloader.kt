package dev.cl0ud9.manager.data.downloads

import android.os.StatFs
import dev.cl0ud9.manager.data.auth.GitHubCredentialStore
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
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404

// safety margin over the artifact size to leave room for the rollback copy and install staging, section 42.6
private const val STORAGE_SAFETY_MARGIN = 1.5
private const val BYTES_PER_MB = 1024L * 1024L
private const val DEFAULT_MIN_FREE_BYTES = 50L * BYTES_PER_MB

// streams the artifact to a resumable .part file, then verifies hash + certificate + package name before
// handing back a ready-to-install path, section 19, 42.5, 42.6, 42.9 of the spec
// one cohesive responsibility (managing downloaded artifacts end to end: download, verify, and now
// also clean up), just with more than detekt's default function-count threshold of small, single-
// purpose steps - splitting it up would fragment that cohesion rather than clarify it
@Suppress("TooManyFunctions")
class OkHttpArtifactDownloader(
    private val downloadsDir: File,
    private val archiveReader: ApkArchiveReader,
    private val credentialStore: GitHubCredentialStore,
    private val httpClient: OkHttpClient = OkHttpClient(),
) : ArtifactDownloader {
    override fun download(
        app: AppProfile,
        artifact: ArtifactInfo,
    ): Flow<DownloadStatus> =
        flow {
            val token = credentialStore.getToken()
            if (artifact.requiresAuth && token == null) {
                emit(DownloadStatus.Failed("This app needs a GitHub access token - add one in Settings."))
                return@flow
            }

            downloadsDir.mkdirs()
            val fileId = fileIdFor(app, artifact)
            val partFile = File(downloadsDir, "$fileId.apk.part")
            val readyFile = File(downloadsDir, "$fileId.apk")

            val preflightFailure = checkStoragePreflight(artifact, token)
            if (preflightFailure != null) {
                emit(DownloadStatus.Failed(preflightFailure))
                return@flow
            }

            val downloadFailure = runDownload(artifact, token, partFile, this)
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

    override fun deleteDownloadedFile(filePath: String) {
        File(filePath).delete()
    }

    override fun clearCache(): Long {
        val files = downloadsDir.listFiles() ?: return 0L
        return files.sumOf { file -> file.length().also { file.delete() } }
    }

    override fun existingReadyFile(
        app: AppProfile,
        artifact: ArtifactInfo,
    ): String? {
        val readyFile = File(downloadsDir, "${fileIdFor(app, artifact)}.apk")
        return if (readyFile.exists()) readyFile.absolutePath else null
    }

    // versionName is part of the file name, not just app.id - a user can now pick an older retained
    // version from App Details' version history, and without this a stale .part/.apk file from a
    // previously-downloaded different version could look resumable/ready here
    private fun fileIdFor(
        app: AppProfile,
        artifact: ArtifactInfo,
    ): String = "${app.id}-${artifact.versionName}"

    // returns a user-facing failure message, or null on success
    private suspend fun runDownload(
        artifact: ArtifactInfo,
        token: String?,
        partFile: File,
        collector: FlowCollector<DownloadStatus>,
    ): String? =
        runCatching { streamDownload(artifact, token, partFile, collector) }
            .fold(onSuccess = { null }, onFailure = { "Download failed: ${it.message ?: "network error"}" })

    private fun checkStoragePreflight(
        artifact: ArtifactInfo,
        token: String?,
    ): String? {
        val contentLength = headContentLength(artifact, token)
        val required = ((contentLength ?: DEFAULT_MIN_FREE_BYTES) * STORAGE_SAFETY_MARGIN).toLong()
        val available = runCatching { StatFs(downloadsDir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (available < required) {
            val requiredMb = required / BYTES_PER_MB
            val availableMb = available / BYTES_PER_MB
            return "Not enough storage: need about ${requiredMb}MB, ${availableMb}MB available."
        }
        return null
    }

    private fun headContentLength(
        artifact: ArtifactInfo,
        token: String?,
    ): Long? =
        runCatching {
            httpClient
                .newCall(authorizedRequestBuilder(artifact, token).head().build())
                .execute()
                .use { response ->
                    response.header("Content-Length")?.toLongOrNull()
                }
        }.getOrNull()

    // a plain browser_download_url needs no headers, but a private/draft release asset's
    // api.github.com URL only returns the actual binary (rather than JSON asset metadata) with both
    // an authenticated request and this Accept header
    private fun authorizedRequestBuilder(
        artifact: ArtifactInfo,
        token: String?,
    ): Request.Builder {
        val builder = Request.Builder().url(artifact.downloadUrl)
        if (artifact.requiresAuth && token != null) {
            builder.header("Authorization", "Bearer $token")
            builder.header("Accept", "application/octet-stream")
        }
        return builder
    }

    // a bare "Server returned HTTP 403" told the user nothing actionable - for a requiresAuth
    // artifact hosted on the private artifacts repo, this almost always means the token is scoped
    // to the wrong repo, was created before being added as a collaborator there, or was revoked
    // (GitHub sometimes reports 404 instead of 403 to avoid confirming a private resource exists
    // to a token that can't see it)
    private fun downloadFailureMessage(
        artifact: ArtifactInfo,
        code: Int,
    ): String {
        val isAuthCode = code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN || code == HTTP_NOT_FOUND
        if (!artifact.requiresAuth || !isAuthCode) return "Server returned HTTP $code"
        return "GitHub rejected the download (HTTP $code). Check that your token in Settings > GitHub " +
            "access is scoped to the artifacts repo you were invited to, and hasn't been revoked."
    }

    // streams the response body to the part file, resuming from its existing length when the server allows it
    private suspend fun streamDownload(
        artifact: ArtifactInfo,
        token: String?,
        partFile: File,
        collector: FlowCollector<DownloadStatus>,
    ) {
        val existingBytes = if (partFile.exists()) partFile.length() else 0L
        val requestBuilder = authorizedRequestBuilder(artifact, token)
        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }
        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) error(downloadFailureMessage(artifact, response.code))
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
