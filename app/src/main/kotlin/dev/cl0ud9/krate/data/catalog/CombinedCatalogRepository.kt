package dev.cl0ud9.krate.data.catalog

import dev.cl0ud9.krate.data.tracked.TrackedAppsRepository
import dev.cl0ud9.krate.domain.model.Announcement
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.repository.CatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// the catalog and the apps someone tracks themselves, as one list, so every screen, check and notification treats a
// tracked app like any other. A tracked app the catalog later picks up gives way to the catalog's entry
class CombinedCatalogRepository(
    private val catalog: CatalogRepository,
    private val tracked: TrackedAppsRepository,
) : CatalogRepository {
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun observeApps(): Flow<List<AppProfile>> =
        combine(catalog.observeApps(), tracked.observeApps()) { listed, own ->
            val listedPackages = listed.mapTo(HashSet()) { it.packageName }
            listed + own.filterNot { it.packageName in listedPackages }
        }

    override fun observeApp(id: String): Flow<AppProfile?> = observeApps().map { list -> list.find { it.id == id } }

    // both at once; a catalog failure still reaches pull-to-refresh, after the tracked apps have had their check
    override suspend fun refresh() {
        coroutineScope {
            val own = async { runCatching { tracked.refresh() } }
            try {
                catalog.refresh()
            } finally {
                own.await()
            }
        }
    }

    override fun refreshInBackground() {
        catalog.refreshInBackground()
        backgroundScope.launch { runCatching { tracked.refresh() } }
    }

    override fun observeAnnouncements(): Flow<List<Announcement>> = catalog.observeAnnouncements()
}
