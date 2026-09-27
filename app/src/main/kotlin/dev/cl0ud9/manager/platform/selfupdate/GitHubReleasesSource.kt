package dev.cl0ud9.manager.platform.selfupdate

import dev.cl0ud9.manager.data.downloads.UserFacingIOException
import dev.cl0ud9.manager.data.downloads.friendlyHttpError
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException

private const val RELEASES_CACHE_FILE = "krate_releases.json"
private const val RELEASES_ETAG_FILE = "krate_releases.etag"
private const val FRESH_FOR_MS = 2 * 60 * 1000L
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_NOT_MODIFIED = 304
private val LIMITED_CODES = setOf(403, 429)

// Krate's own GitHub releases list, fetched politely: a saved token when there is one, a short-lived copy
// in memory, and an ETag so unchanged replies cost nothing against GitHub's limit
internal class GitHubReleasesSource(
    cacheDir: File,
    private val httpClient: OkHttpClient,
    private val url: String,
    private val tokenProvider: () -> String?,
) {
    private val cacheFile = File(cacheDir, RELEASES_CACHE_FILE)
    private val etagFile = File(cacheDir, RELEASES_ETAG_FILE)

    @Volatile private var lastFetchMillis = 0L

    // the releases JSON: reused for a couple of minutes, then revalidated with its ETag - an unchanged
    // reply (304) doesn't count against GitHub's limit - and the last copy stands in if GitHub is limiting
    @Synchronized
    fun fetch(
        allowStale: Boolean,
        revalidate: Boolean = false,
    ): String {
        val cached = cacheFile.takeIf { it.exists() }?.readText()
        val fresh = !revalidate && cached != null && System.currentTimeMillis() - lastFetchMillis < FRESH_FOR_MS
        return if (fresh && cached != null) cached else fetchReleasesBody(cached, allowStale)
    }

    // offline, What's new shows the last list Krate fetched; an update check reports the failure instead
    private fun fetchReleasesBody(
        cached: String?,
        allowStale: Boolean,
    ): String {
        val response =
            try {
                openReleases(cached)
            } catch (exception: IOException) {
                if (allowStale && cached != null) return cached
                throw exception
            }
        return response.use { readReleases(it, cached) }
    }

    // a revoked or mistyped token shouldn't break the check - GitHub answers public data without one
    private fun openReleases(cached: String?): Response {
        val token = tokenProvider()
        val response = httpClient.newCall(releasesRequest(token, cached)).execute()
        if (response.code != HTTP_UNAUTHORIZED || token == null) return response
        response.close()
        return httpClient.newCall(releasesRequest(null, cached)).execute()
    }

    private fun readReleases(
        response: Response,
        cached: String?,
    ): String =
        when {
            response.code == HTTP_NOT_MODIFIED && cached != null ->
                cached.also {
                    lastFetchMillis =
                        System.currentTimeMillis()
                }
            response.isSuccessful -> storeReleases(response)
            cached != null && response.code in LIMITED_CODES -> cached
            else -> fail(friendlyHttpError(response.code))
        }

    private fun releasesRequest(
        token: String?,
        cached: String?,
    ): Request =
        Request
            .Builder()
            .url(url)
            .apply {
                if (token != null) header("Authorization", "Bearer $token")
                val etag = etagFile.takeIf { cached != null && it.exists() }?.readText()
                if (!etag.isNullOrBlank()) header("If-None-Match", etag)
            }.build()

    private fun storeReleases(response: Response): String {
        val body = response.body?.string() ?: fail("GitHub sent an empty reply. Try again.")
        runCatching {
            cacheFile.writeText(body)
            response.header("ETag")?.let(etagFile::writeText) ?: etagFile.delete()
        }
        lastFetchMillis = System.currentTimeMillis()
        return body
    }

    private fun fail(message: String): Nothing = throw UserFacingIOException(message)
}
