package dev.cl0ud9.manager.data.catalog

import dev.cl0ud9.manager.data.auth.GitHubCredentialStore
import dev.cl0ud9.manager.security.manifest.ManifestVerifier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

// the invite-only shelf's own catalog, published as a release on a private repo and signed with its own key
private const val PRIVATE_CATALOG_RELEASE_URL =
    "https://api.github.com/repos/Cl0ud-9/krate-artifacts/releases/tags/catalog-latest"
const val PRIVATE_CATALOG_PUBLIC_KEY_BASE64 = "5Dz9s9HlIIAoGeUiTz5OuK91w0Q7iLVeNX8S/FclneU="
private const val MANIFEST_ASSET = "manifest.json"
private const val SIGNATURE_ASSET = "manifest.json.sig"

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
) {
    private val json = Json { ignoreUnknownKeys = true }

    // the last verified copy, or null without a token (invited entries leave with it)
    fun cached(): ByteArray? =
        if (credentialStore.getToken() ==
            null
        ) {
            null
        } else {
            runCatching { cacheFile.takeIf { it.exists() }?.readBytes() }.getOrNull()
        }

    // a fresh copy replaces the cached one; a failed fetch keeps it, so invited entries don't vanish while offline
    fun refresh() {
        if (credentialStore.getToken() == null) {
            cacheFile.delete()
        } else {
            fetchVerified()?.let { cacheFile.writeBytes(it) }
        }
    }

    // the verified catalog bytes, or null without a token or when any step fails (no access, offline, bad signature)
    private fun fetchVerified(): ByteArray? =
        credentialStore.getToken()?.let { token ->
            val assets =
                get(PRIVATE_CATALOG_RELEASE_URL, token, accept = "application/vnd.github+json")
                    ?.let {
                        runCatching {
                            json
                                .decodeFromString<PrivateReleaseDto>(
                                    it.decodeToString(),
                                ).assets
                        }.getOrNull()
                    }.orEmpty()
            val manifest = assets.find { it.name == MANIFEST_ASSET }?.let { get(it.url, token) }
            val signature = assets.find { it.name == SIGNATURE_ASSET }?.let { get(it.url, token) }
            manifest?.takeIf { signature != null && verifier.verify(it, signature) }
        }

    private fun get(
        url: String,
        token: String,
        accept: String = "application/octet-stream",
    ): ByteArray? =
        runCatching {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", accept)
                    .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        }.getOrNull()
}
