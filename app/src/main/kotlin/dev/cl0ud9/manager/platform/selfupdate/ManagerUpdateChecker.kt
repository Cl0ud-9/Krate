package dev.cl0ud9.manager.platform.selfupdate

import android.content.Context
import dev.cl0ud9.manager.data.downloads.UserFacingIOException
import dev.cl0ud9.manager.data.downloads.friendlyNetworkError
import dev.cl0ud9.manager.domain.version.isNewerVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val RELEASES_API_URL = "https://api.github.com/repos/Cl0ud-9/krate/releases"
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
        // the release's own notes (markdown), shown with the update prompt
        val releaseNotes: String?,
        // null when the release has no .apk asset attached (shouldn't happen for a release built by
        // this repo's own pipeline, but a release created by hand could omit it) - the UI falls back
        // to "view on GitHub" in that case instead of offering a download button with nothing to fetch
        val downloadUrl: String?,
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
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GithubReleaseAssetDto> = emptyList(),
)

// one manager release, for the What's new sheet
data class ManagerRelease(
    val version: String,
    val publishedAt: String?,
    val notes: String,
)

@Serializable
private data class GithubReleaseAssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

private fun defaultHttpClient(): OkHttpClient =
    OkHttpClient
        .Builder()
        .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

// checks the manager's own GitHub Releases page against the installed version, amendment 44.2 of the
// spec. the check itself is fully real: it calls GitHub's public Releases API and compares against
// the actual installed versionName, it never fabricates availability (section 32 of the spec)
class ManagerUpdateChecker(
    context: Context,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val releasesApiUrl: String = RELEASES_API_URL,
    // a saved GitHub token lifts GitHub's limit from 60 to 5,000 requests an hour
    private val tokenProvider: () -> String? = { null },
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val source = GitHubReleasesSource(appContext.cacheDir, httpClient, releasesApiUrl, tokenProvider)

    // revalidate when the user asked: a check they tapped goes to GitHub even if a moment-old answer is on hand
    suspend fun check(revalidate: Boolean = false): ManagerUpdateStatus =
        withContext(Dispatchers.IO) {
            val installedVersion = installedVersionName()
            if (installedVersion == null) {
                ManagerUpdateStatus.Failed("Couldn't read the installed version.")
            } else {
                try {
                    toStatus(fetchAppReleases(revalidate = revalidate).firstOrNull(), installedVersion)
                } catch (exception: IOException) {
                    ManagerUpdateStatus.Failed(friendlyNetworkError(exception))
                }
            }
        }

    private fun toStatus(
        release: GithubReleaseDto?,
        installedVersion: String,
    ): ManagerUpdateStatus {
        if (release == null) return ManagerUpdateStatus.NoReleasePublished
        val latestVersion = release.tagName.removePrefix("v")
        return if (isNewerVersion(latestVersion, installedVersion)) {
            val apkUrl = release.assets.firstOrNull { it.name.endsWith(".apk") }?.browserDownloadUrl
            ManagerUpdateStatus.UpdateAvailable(latestVersion, release.htmlUrl, release.body, apkUrl)
        } else {
            ManagerUpdateStatus.UpToDate
        }
    }

    // the newest manager releases with their notes, for What's new - throws IOException with a
    // user-facing message (see friendlyNetworkError) when GitHub can't be reached
    suspend fun recentReleases(): List<ManagerRelease> =
        withContext(Dispatchers.IO) {
            fetchAppReleases(allowStale = true).map {
                ManagerRelease(it.tagName.removePrefix("v"), it.publishedAt, it.body.orEmpty())
            }
        }

    // GitHub returns releases newest-first; the reserved manifest release tag is not a manager release
    private fun fetchAppReleases(
        allowStale: Boolean = false,
        revalidate: Boolean = false,
    ): List<GithubReleaseDto> {
        val body = source.fetch(allowStale, revalidate)
        val releases: List<GithubReleaseDto> =
            runCatching { json.decodeFromString<List<GithubReleaseDto>>(body) }
                .getOrElse { fail("GitHub sent an unexpected reply. Try again later.") }
        return releases.filter { it.tagName != MANIFEST_RELEASE_TAG }
    }

    private fun fail(message: String): Nothing = throw UserFacingIOException(message)

    private fun installedVersionName(): String? =
        runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        }.getOrNull()
}
