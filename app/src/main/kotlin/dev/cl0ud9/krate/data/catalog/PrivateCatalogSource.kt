package dev.cl0ud9.krate.data.catalog

import dev.cl0ud9.krate.data.auth.GitHubCredentialStore
import dev.cl0ud9.krate.domain.model.InviteStatus
import dev.cl0ud9.krate.security.manifest.ManifestVerifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

// the invite-only shelf's own catalog, published as a release on a private repo and signed with its own key
private const val PRIVATE_CATALOG_RELEASE_URL =
    "https://api.github.com/repos/Cl0ud-9/Krate-artifacts/releases/tags/catalog-latest"
const val PRIVATE_CATALOG_PUBLIC_KEY_BASE64 = "5Dz9s9HlIIAoGeUiTz5OuK91w0Q7iLVeNX8S/FclneU="
private const val MANIFEST_ASSET = "manifest.json"
private const val SIGNATURE_ASSET = "manifest.json.sig"
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_NOT_FOUND = 404

@Serializable
private data class PrivateReleaseDto(
    val assets: List<PrivateAssetDto> = emptyList(),
)

@Serializable
private data class PrivateAssetDto(
    val name: String,
    val url: String,
)

// reachable only with the saved GitHub token, so invited people see these entries and nobody else can fetch them
class PrivateCatalogSource(
    private val credentialStore: GitHubCredentialStore,
    private val httpClient: OkHttpClient,
    private val cacheFile: File,
    private val verifier: ManifestVerifier = ManifestVerifier(PRIVATE_CATALOG_PUBLIC_KEY_BASE64),
    private val releaseUrl: String = PRIVATE_CATALOG_RELEASE_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // lazy, so building the app container never reads the encrypted token store on the main thread
    private val mutableStatus by lazy { MutableStateFlow(initialStatus()) }
    val status: StateFlow<InviteStatus> by lazy { mutableStatus.asStateFlow() }

    // the last verified copy, or null without a token (invited entries leave with it)
    fun cached(): ByteArray? =
        credentialStore.getToken()?.let {
            runCatching { cacheFile.takeIf { it.exists() }?.readBytes() }.getOrNull()
        }

    // a fresh copy replaces the cached one; a revoked token drops it; any other failure keeps it for offline use
    fun refresh() {
        val token = credentialStore.getToken()
        if (token != null && mutableStatus.value !is InviteStatus.Open) mutableStatus.value = InviteStatus.Checking
        when (val result = token?.let(::fetchVerified) ?: Fetch.Denied) {
            is Fetch.Verified -> {
                cacheFile.writeBytes(result.bytes)
                mutableStatus.value = InviteStatus.Open(appCount(result.bytes))
            }
            Fetch.Denied -> {
                cacheFile.delete()
                mutableStatus.value = if (token == null) InviteStatus.NoToken else InviteStatus.Rejected
            }
            Fetch.Failed -> mutableStatus.value = InviteStatus.Unreachable
        }
    }

    // a verified copy from an earlier run already counts as open until the next check says otherwise
    private fun initialStatus(): InviteStatus =
        when {
            credentialStore.getToken() == null -> InviteStatus.NoToken
            else -> cached()?.let { InviteStatus.Open(appCount(it)) } ?: InviteStatus.Unchecked
        }

    private fun appCount(catalog: ByteArray): Int =
        runCatching {
            val root = json.parseToJsonElement(catalog.decodeToString()).jsonObject
            root["apps"]?.jsonArray?.size
        }.getOrNull() ?: 0

    // 401 means the token is expired or revoked, 404 means it can't see the repo; both end the invite
    private fun fetchVerified(token: String): Fetch {
        val release = get(releaseUrl, token, accept = "application/vnd.github+json")
        if (release.code == HTTP_UNAUTHORIZED || release.code == HTTP_NOT_FOUND) return Fetch.Denied
        val assets = release.body?.let(::parseAssets).orEmpty()
        val manifest = assets.find { it.name == MANIFEST_ASSET }?.let { get(it.url, token).body }
        val signature = assets.find { it.name == SIGNATURE_ASSET }?.let { get(it.url, token).body }
        val verified = manifest?.takeIf { signature != null && verifier.verify(it, signature) }
        return verified?.let(Fetch::Verified) ?: Fetch.Failed
    }

    private fun parseAssets(body: ByteArray): List<PrivateAssetDto>? =
        runCatching { json.decodeFromString<PrivateReleaseDto>(body.decodeToString()).assets }.getOrNull()

    // code is 0 when the request never got an answer (offline, timeout)
    private fun get(
        url: String,
        token: String,
        accept: String = "application/octet-stream",
    ): HttpResult =
        runCatching {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", accept)
                    .build()
            httpClient.newCall(request).execute().use { response ->
                HttpResult(response.code, if (response.isSuccessful) response.body?.bytes() else null)
            }
        }.getOrElse { HttpResult(0, null) }

    private class HttpResult(
        val code: Int,
        val body: ByteArray?,
    )

    private sealed interface Fetch {
        class Verified(
            val bytes: ByteArray,
        ) : Fetch

        data object Denied : Fetch

        data object Failed : Fetch
    }
}
