package dev.cl0ud9.manager.data.catalog

import android.content.Context
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import dev.cl0ud9.manager.security.manifest.ManifestVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private const val MANIFEST_URL = "https://github.com/Cl0ud-9/manager/releases/download/manifest-latest/manifest.json"
private const val SIGNATURE_URL = "$MANIFEST_URL.sig"
private const val CACHE_FILE_NAME = "manifest-cache.json"

// bounds worst-case first-launch latency before falling back, section 32 never blocks the ui indefinitely
private const val NETWORK_TIMEOUT_SECONDS = 8L

private fun defaultHttpClient(): OkHttpClient =
    OkHttpClient
        .Builder()
        .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

// fetches the signed remote manifest, section 9 + amendment 44.3 of the spec
// falls back to the last verified cache, then to the seed asset, never fabricates availability (section 32)
//
// this repository is a singleton for the app's process lifetime (owned by AppContainer), and holds its
// last-fetched result in memory so every screen shares one cache instead of each independently
// re-fetching: a screen's own resume-triggered refresh() only re-checks device-local installed state
// (cheap, no network), while refresh() here is the one place that actually hits the network again -
// pull-to-refresh and the WorkManager periodic check both go through this, and whichever screens are
// on screen at the time all see the result reactively, since they share this one MutableStateFlow
class RemoteCatalogRepository(
    context: Context,
    private val fallback: CatalogRepository,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val verifier: ManifestVerifier = ManifestVerifier(),
) : CatalogRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
    private val apps = MutableStateFlow<List<AppProfile>?>(null)
    private val loadMutex = Mutex()

    override fun observeApps(): Flow<List<AppProfile>> = apps.onSubscription { ensureLoaded() }.filterNotNull()

    override fun observeApp(id: String): Flow<AppProfile?> = observeApps().map { list -> list.find { it.id == id } }

    override suspend fun refresh() {
        apps.value = loadApps()
    }

    // only the first subscriber (across the whole app) actually pays for a fetch - later ones, even on
    // a different screen, get what's already cached instantly. Guarded by a mutex so two screens
    // subscribing at nearly the same moment (e.g. Home and Apps both composing on cold start) can't
    // both kick off a redundant simultaneous fetch
    private suspend fun ensureLoaded() {
        if (apps.value != null) return
        loadMutex.withLock {
            if (apps.value != null) return
            apps.value = loadApps()
        }
    }

    private suspend fun loadApps(): List<AppProfile> =
        withContext(Dispatchers.IO) {
            val fromNetwork =
                fetchVerifiedManifestBytes()?.let {
                    cacheFile.writeBytes(it)
                    parseManifest(it)
                }
            fromNetwork ?: loadCachedManifest() ?: fallback.observeApps().first()
        }

    private fun loadCachedManifest(): List<AppProfile>? {
        val cached = runCatching { cacheFile.takeIf { it.exists() }?.readBytes() }.getOrNull() ?: return null
        return runCatching { parseManifest(cached) }.getOrNull()
    }

    private suspend fun fetchVerifiedManifestBytes(): ByteArray? =
        coroutineScope {
            val manifestDeferred = async { downloadOrNull(MANIFEST_URL) }
            val signatureDeferred = async { downloadOrNull(SIGNATURE_URL) }
            val (manifestBytes, signatureBytes) = awaitAll(manifestDeferred, signatureDeferred)
            manifestBytes?.takeIf { signatureBytes != null && verifier.verify(it, signatureBytes) }
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
