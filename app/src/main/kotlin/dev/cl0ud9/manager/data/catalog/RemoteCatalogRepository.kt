package dev.cl0ud9.manager.data.catalog

import android.content.Context
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import dev.cl0ud9.manager.security.manifest.ManifestVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private const val MANIFEST_URL = "https://github.com/Cl0ud-9/manager/releases/download/manifest-latest/manifest.json"
private const val SIGNATURE_URL = "$MANIFEST_URL.sig"
private const val CACHE_FILE_NAME = "manifest-cache.json"

// fetches the signed remote manifest, section 9 + amendment 44.3 of the spec
// falls back to the last verified cache, then to the seed asset, never fabricates availability (section 32)
class RemoteCatalogRepository(
    context: Context,
    private val fallback: CatalogRepository,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val verifier: ManifestVerifier = ManifestVerifier(),
) : CatalogRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile = File(context.filesDir, CACHE_FILE_NAME)

    override fun observeApps(): Flow<List<AppProfile>> = flow { emit(loadApps()) }

    override fun observeApp(id: String): Flow<AppProfile?> = observeApps().map { apps -> apps.find { it.id == id } }

    private suspend fun loadApps(): List<AppProfile> {
        val fromNetwork =
            withContext(Dispatchers.IO) { fetchVerifiedManifestBytes() }?.let {
                cacheFile.writeBytes(it)
                parseManifest(it)
            }
        return fromNetwork ?: loadCachedManifest() ?: fallback.observeApps().first()
    }

    private fun loadCachedManifest(): List<AppProfile>? {
        val cached = runCatching { cacheFile.takeIf { it.exists() }?.readBytes() }.getOrNull() ?: return null
        return runCatching { parseManifest(cached) }.getOrNull()
    }

    private fun fetchVerifiedManifestBytes(): ByteArray? {
        val manifestBytes = downloadOrNull(MANIFEST_URL)
        val signatureBytes = downloadOrNull(SIGNATURE_URL)
        return manifestBytes?.takeIf { signatureBytes != null && verifier.verify(it, signatureBytes) }
    }

    private fun downloadOrNull(url: String): ByteArray? =
        runCatching {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        }.getOrNull()

    private fun parseManifest(bytes: ByteArray): List<AppProfile> =
        json.decodeFromString<ManifestDto>(bytes.decodeToString()).apps.map { it.toDomain() }
}
