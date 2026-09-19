package dev.cl0ud9.manager.platform.selfupdate

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val RELEASES_API_URL = "https://api.github.com/repos/Cl0ud-9/manager/releases"
private const val NETWORK_TIMEOUT_SECONDS = 8L

// this repo also publishes a GitHub Release under this exact tag purely to host the signed catalog
// manifest.json (see manifest.yml / RemoteCatalogRepository) - it is not a manager app release, so
// treating "latest release" naively would report the manifest publish itself as an available manager
// update the first time this ships (confirmed live against the real repo during testing)
private const val MANIFEST_RELEASE_TAG = "manifest-latest"

sealed interface ManagerUpdateStatus {
    data object UpToDate : ManagerUpdateStatus

    data class UpdateAvailable(
        val latestVersion: String,
        val releaseUrl: String,
    ) : ManagerUpdateStatus

    // distinct from Failed: the check itself succeeded, there is just genuinely no manager release
    // published yet - this repo does not currently ship signed manager release APKs
    data object NoReleasePublished : ManagerUpdateStatus

    data class Failed(
        val reason: String,
    ) : ManagerUpdateStatus
}

@Serializable
private data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
)

// dot-separated numeric comparison (1.4.10 vs 1.4.9) with a plain-inequality fallback for tags that
// don't parse as numeric segments, rather than silently treating every mismatch as "newer". A
// top-level function (not a private method on the checker) so it's directly unit-testable without
// needing a Context or a network mock - the actual number comparison is the one part of this whole
// feature that must never be wrong, since it decides whether users are told an update exists at all
internal fun isNewerVersion(
    latest: String,
    installed: String,
): Boolean {
    val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
    val installedParts = installed.split(".").mapNotNull { it.toIntOrNull() }
    return if (latestParts.isEmpty() || installedParts.isEmpty()) {
        latest != installed
    } else {
        compareVersionSegments(latestParts, installedParts) > 0
    }
}

private fun compareVersionSegments(
    latestParts: List<Int>,
    installedParts: List<Int>,
): Int {
    val length = maxOf(latestParts.size, installedParts.size)
    for (index in 0 until length) {
        val latestSegment = latestParts.getOrElse(index) { 0 }
        val installedSegment = installedParts.getOrElse(index) { 0 }
        val comparison = latestSegment.compareTo(installedSegment)
        if (comparison != 0) return comparison
    }
    return 0
}

private fun defaultHttpClient(): OkHttpClient =
    OkHttpClient
        .Builder()
        .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

// checks the manager's own GitHub Releases page against the installed version, amendment 44.2 of the
// spec. this repo does not yet publish a signed release APK as a build artifact - that needs external
// release-signing infrastructure this can't set up on its own - so an available update opens the
// release page for a manual download rather than attempting a silent in-app self-install. the check
// itself is fully real: it calls GitHub's public Releases API and compares against the actual
// installed versionName, it never fabricates availability (section 32 of the spec)
class ManagerUpdateChecker(
    context: Context,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val releasesApiUrl: String = RELEASES_API_URL,
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): ManagerUpdateStatus =
        withContext(Dispatchers.IO) {
            val installedVersion = installedVersionName()
            if (installedVersion == null) {
                ManagerUpdateStatus.Failed("Could not read the installed version.")
            } else {
                runCatching { fetchLatestAppRelease() }
                    .fold(
                        onSuccess = { release -> toStatus(release, installedVersion) },
                        onFailure = { ManagerUpdateStatus.Failed(it.message ?: "Check failed.") },
                    )
            }
        }

    private fun toStatus(
        release: GithubReleaseDto?,
        installedVersion: String,
    ): ManagerUpdateStatus {
        if (release == null) return ManagerUpdateStatus.NoReleasePublished
        val latestVersion = release.tagName.removePrefix("v")
        return if (isNewerVersion(latestVersion, installedVersion)) {
            ManagerUpdateStatus.UpdateAvailable(latestVersion, release.htmlUrl)
        } else {
            ManagerUpdateStatus.UpToDate
        }
    }

    // GitHub returns releases newest-first, so the first entry that isn't the reserved manifest
    // release tag is the most recent genuine manager release, if any exists yet
    private fun fetchLatestAppRelease(): GithubReleaseDto? {
        httpClient.newCall(Request.Builder().url(releasesApiUrl).build()).execute().use { response ->
            check(response.isSuccessful) { "GitHub returned ${response.code}." }
            val body = response.body?.string() ?: error("Empty response.")
            val releases: List<GithubReleaseDto> = json.decodeFromString(body)
            return releases.firstOrNull { it.tagName != MANIFEST_RELEASE_TAG }
        }
    }

    private fun installedVersionName(): String? =
        runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        }.getOrNull()
}
