package dev.cl0ud9.manager.domain.repository

import dev.cl0ud9.manager.domain.model.Announcement
import dev.cl0ud9.manager.domain.model.AppProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// the signed remote manifest, falling back to its last verified cache and then the bundled seed
interface CatalogRepository {
    fun observeApps(): Flow<List<AppProfile>>

    fun observeApp(id: String): Flow<AppProfile?>

    // forces a genuine re-fetch, for pull-to-refresh and the WorkManager fallback check - without
    // this, every screen's "refresh" only re-checked device-local installed state (see the
    // ViewModels' own refresh()), since observeApps() was a single-shot cold flow that any long-lived
    // subscriber (a StateFlow collector) only ever triggered once
    suspend fun refresh()

    // curated notices from the same signed manifest - only the remote catalog has any
    fun observeAnnouncements(): Flow<List<Announcement>> = flowOf(emptyList())
}
