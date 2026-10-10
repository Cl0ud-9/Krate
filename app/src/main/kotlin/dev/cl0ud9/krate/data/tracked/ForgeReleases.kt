package dev.cl0ud9.krate.data.tracked

import dev.cl0ud9.krate.domain.tracked.Forge
import dev.cl0ud9.krate.domain.tracked.ReleaseAsset
import dev.cl0ud9.krate.domain.tracked.RepoRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.time.Instant

private const val GITHUB_API = "https://api.github.com"
private const val RELEASES_PER_CHECK = 15
private const val HTTP_OK = 200
private const val HTTP_NOT_MODIFIED = 304
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY = 429
private const val DIGEST_PREFIX = "sha256:"

// a repository's own description, the one thing read from it; every site calls it the same
@Serializable
data class RepoDto(
    val description: String? = null,
)

@Serializable
internal data class ReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<AssetDto> = emptyList(),
)

@Serializable
internal data class AssetDto(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
    val digest: String? = null,
)

// GitLab's releases: notes are the description, files are links, and there's no pre-release flag
@Serializable
internal data class GitLabReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val description: String? = null,
    @SerialName("released_at") val releasedAt: String? = null,
    @SerialName("upcoming_release") val upcoming: Boolean = false,
    val assets: GitLabAssetsDto = GitLabAssetsDto(),
)

@Serializable
internal data class GitLabAssetsDto(
    val links: List<GitLabLinkDto> = emptyList(),
)

@Serializable
internal data class GitLabLinkDto(
    val name: String,
    val url: String,
    @SerialName("direct_asset_url") val directUrl: String? = null,
)

// one release, ready for Krate to read
data class Release(
    val tag: String,
    val notes: String?,
    val prerelease: Boolean,
    val publishedAtMillis: Long?,
    val assets: List<ReleaseAsset>,
)

// what asking a site can come back with
sealed interface ForgeAnswer<out T> {
    data class Found<T>(
        val value: T,
        // the site's tag for this answer, so the next check can ask "anything new?" without spending its allowance
        val etag: String? = null,
    ) : ForgeAnswer<T>

    // nothing changed since the tag that was sent
    data object Unchanged : ForgeAnswer<Nothing>

    data object NotFound : ForgeAnswer<Nothing>

    // out of checks for now; on GitHub a saved token raises the allowance from 60 an hour to 5,000
    data class OutOfChecks(
        val withToken: Boolean,
    ) : ForgeAnswer<Nothing>

    // no answer at all: offline, or the site was down
    data object Unreachable : ForgeAnswer<Nothing>
}

// reads public repositories and their releases from GitHub, Codeberg and GitLab; the saved token goes to GitHub only
class ForgeReleases(
    private val httpClient: OkHttpClient,
    private val token: () -> String?,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun repo(ref: RepoRef): ForgeAnswer<RepoDto> =
        get(ref.forge, repoUrl(ref), etag = null) { json.decodeFromString<RepoDto>(it) }

    // newest first, drafts and not-yet-out releases left out
    fun releases(
        ref: RepoRef,
        etag: String?,
    ): ForgeAnswer<List<Release>> =
        get(ref.forge, "${repoUrl(ref)}/releases?${pageSize(ref.forge)}=$RELEASES_PER_CHECK", etag) { body ->
            when (ref.forge) {
                Forge.GITLAB ->
                    json.decodeFromString<List<GitLabReleaseDto>>(body).filterNot { it.upcoming }.map { it.toRelease() }
                else -> json.decodeFromString<List<ReleaseDto>>(body).filterNot { it.draft }.map { it.toRelease() }
            }
        }

    private fun repoUrl(ref: RepoRef): String =
        when (ref.forge) {
            Forge.GITHUB -> "$GITHUB_API/repos/${ref.slug}"
            Forge.CODEBERG -> "https://codeberg.org/api/v1/repos/${ref.slug}"
            // GitLab names a project by its whole path, escaped, groups and all
            Forge.GITLAB -> "https://gitlab.com/api/v4/projects/${URLEncoder.encode(ref.slug, Charsets.UTF_8.name())}"
        }

    private fun pageSize(forge: Forge): String = if (forge == Forge.CODEBERG) "limit" else "per_page"

    private fun <T> get(
        forge: Forge,
        url: String,
        etag: String?,
        read: (String) -> T,
    ): ForgeAnswer<T> {
        val saved = token().takeIf { forge == Forge.GITHUB }
        val answer = request(url, etag, saved, read)
        // a token that stopped working shouldn't stop public checks: ask again as a guest
        return if (answer is Rejected && saved != null) request(url, etag, null, read).result else answer.result
    }

    private fun <T> request(
        url: String,
        etag: String?,
        withToken: String?,
        read: (String) -> T,
    ): Outcome<T> =
        runCatching {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .apply {
                        if (url.startsWith(GITHUB_API)) {
                            header("Accept", "application/vnd.github+json")
                            header("X-GitHub-Api-Version", "2022-11-28")
                        }
                        withToken?.let { header("Authorization", "Bearer $it") }
                        etag?.let { header("If-None-Match", it) }
                    }.build()
            httpClient.newCall(request).execute().use { response ->
                val outOfChecks = response.header("x-ratelimit-remaining") == "0"
                when {
                    response.code == HTTP_NOT_MODIFIED -> Answered(ForgeAnswer.Unchanged)
                    response.code == HTTP_OK ->
                        Answered(ForgeAnswer.Found(read(response.body?.string().orEmpty()), response.header("ETag")))
                    response.code == HTTP_UNAUTHORIZED -> Rejected
                    response.code == HTTP_TOO_MANY || (response.code == HTTP_FORBIDDEN && outOfChecks) ->
                        Answered(ForgeAnswer.OutOfChecks(withToken != null))
                    response.code == HTTP_NOT_FOUND -> Answered(ForgeAnswer.NotFound)
                    else -> Answered(ForgeAnswer.Unreachable)
                }
            }
        }.getOrElse { Answered(ForgeAnswer.Unreachable) }

    private sealed interface Outcome<out T> {
        val result: ForgeAnswer<T>
    }

    private class Answered<T>(
        override val result: ForgeAnswer<T>,
    ) : Outcome<T>

    private data object Rejected : Outcome<Nothing> {
        override val result: ForgeAnswer<Nothing> = ForgeAnswer.Unreachable
    }
}

internal fun ReleaseDto.toRelease(): Release =
    Release(
        tag = tagName,
        notes = body?.takeIf { it.isNotBlank() },
        prerelease = prerelease,
        publishedAtMillis = publishedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
        assets =
            assets.map { asset ->
                ReleaseAsset(
                    name = asset.name,
                    downloadUrl = asset.downloadUrl,
                    sizeBytes = asset.size,
                    sha256 = asset.digest?.takeIf { it.startsWith(DIGEST_PREFIX) }?.removePrefix(DIGEST_PREFIX),
                )
            },
    )

// a tag that names itself a test build: GitLab has no flag for it, so the name is all there is to go on
private val TEST_TAG = Regex("""(?i)(^|[^a-z])(alpha|beta|rc|pre|preview|nightly|dev)\d*([^a-z]|$)""")

internal fun GitLabReleaseDto.toRelease(): Release =
    Release(
        tag = tagName,
        notes = description?.takeIf { it.isNotBlank() },
        prerelease = TEST_TAG.containsMatchIn(tagName),
        publishedAtMillis = releasedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
        assets =
            assets.links.map { link ->
                // GitLab doesn't say how big a linked file is; the download finds out
                ReleaseAsset(name = link.name, downloadUrl = link.directUrl ?: link.url, sizeBytes = 0)
            },
    )
