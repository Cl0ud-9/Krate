package dev.cl0ud9.manager.data.catalog

import android.content.Context
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

// reads the bundled seed catalog, swap for a manifest-backed implementation in phase 2
class AssetCatalogRepository(
    private val context: Context,
) : CatalogRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override fun observeApps(): Flow<List<AppProfile>> = flow { emit(loadCatalog()) }

    override fun observeApp(id: String): Flow<AppProfile?> = observeApps().map { apps -> apps.find { it.id == id } }

    private suspend fun loadCatalog(): List<AppProfile> =
        withContext(Dispatchers.IO) {
            val text =
                context.assets
                    .open(SEED_ASSET)
                    .bufferedReader()
                    .use { it.readText() }
            json.decodeFromString<CatalogDto>(text).apps.map { it.toDomain() }
        }

    private companion object {
        const val SEED_ASSET = "seed-catalog.json"
    }
}
