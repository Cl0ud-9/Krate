package dev.cl0ud9.manager.data.catalog

import android.content.Context
import android.os.Build
import dev.cl0ud9.manager.domain.model.Announcement
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.DeviceProfile
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
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val MANIFEST_URL = "https://github.com/Cl0ud-9/manager/releases/download/manifest-latest/manifest.json"
private const val SIGNATURE_URL = "$MANIFEST_URL.sig"
private const val CACHE_FILE_NAME = "manifest-cache.json"

// bounds worst-case first-launch latency before falling back, section 32 never blocks the ui indefinitely
private const val NETWORK_TIMEOUT_SECONDS = 8L

private fun currentDevice(context: Context): DeviceProfile =
    DeviceProfile(
        sdkInt = Build.VERSION.SDK_INT,
        supportedAbis = Build.SUPPORTED_ABIS.toList(),
        managerVersionCode =
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode }
                .getOrDefault(0L),
    )

private class ParsedManifest(
    val apps: List<AppProfile>,
    val announcements: List<Announcement>,
)

private fun parseManifest(
    json: Json,
    device: DeviceProfile,
    bytes: ByteArray,
): ParsedManifest {
    val manifest = json.decodeFromString<ManifestDto>(bytes.decodeToString())
    return ParsedManifest(
        apps = manifest.apps.mapNotNull { it.toDomain(device) },
        announcements = manifest.announcements.mapNotNull { it.toDomain(device) },
    )
}

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
    private val device: DeviceProfile = currentDevice(context),
) : CatalogRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
    private val apps = MutableStateFlow<List<AppProfile>?>(null)
    private val announcements = MutableStateFlow<List<Announcement>>(emptyList())
    private val loadMutex = Mutex()

    override fun observeApps(): Flow<List<AppProfile>> = apps.onSubscription { ensureLoaded() }.filterNotNull()

    override fun observeApp(id: String): Flow<AppProfile?> = observeApps().map { list -> list.find { it.id == id } }

    override fun observeAnnouncements(): Flow<List<Announcement>> = announcements.onSubscription { ensureLoaded() }

    // a failed download keeps what's already showing and throws, so pull-to-refresh can say so
    override suspend fun refresh() {
        val bytes = withContext(Dispatchers.IO) { fetchVerifiedManifestBytes() }
        if (bytes == null) {
            ensureLoaded()
            throw IOException("Couldn't reach the catalog")
        }
        val manifest =
            withContext(Dispatchers.IO) {
                cacheFile.writeBytes(bytes)
                parseManifest(json, device, bytes)
            }
        publish(manifest)
    }

    // only the first subscriber (across the whole app) actually pays for a fetch - later ones, even on
    // a different screen, get what's already cached instantly. Guarded by a mutex so two screens
    // subscribing at nearly the same moment (e.g. Home and Apps both composing on cold start) can't
    // both kick off a redundant simultaneous fetch
    private suspend fun ensureLoaded() {
        if (apps.value != null) return
        loadMutex.withLock {
            if (apps.value != null) return
            publish(loadManifest())
        }
    }

    // announcements first, so a screen reacting to the new app list already sees its notices
    private fun publish(manifest: ParsedManifest) {
        announcements.value = manifest.announcements
        apps.value = manifest.apps
    }

    private suspend fun loadManifest(): ParsedManifest =
        withContext(Dispatchers.IO) {
            val fromNetwork =
                fetchVerifiedManifestBytes()?.let {
                    cacheFile.writeBytes(it)
                    parseManifest(json, device, it)
                }
            fromNetwork ?: loadCachedManifest() ?: ParsedManifest(fallback.observeApps().first(), emptyList())
        }

    private fun loadCachedManifest(): ParsedManifest? {
        val cached = runCatching { cacheFile.takeIf { it.exists() }?.readBytes() }.getOrNull() ?: return null
        return runCatching { parseManifest(json, device, cached) }.getOrNull()
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
}
