package dev.cl0ud9.manager.domain.repository

import dev.cl0ud9.manager.domain.model.AppProfile
import kotlinx.coroutines.flow.Flow

// backed by a bundled seed asset until manifest ingestion lands in phase 2
interface CatalogRepository {
    fun observeApps(): Flow<List<AppProfile>>

    fun observeApp(id: String): Flow<AppProfile?>

    // forces a genuine re-fetch, for pull-to-refresh and the WorkManager fallback check - without
    // this, every screen's "refresh" only re-checked device-local installed state (see the
    // ViewModels' own refresh()), since observeApps() was a single-shot cold flow that any long-lived
    // subscriber (a StateFlow collector) only ever triggered once
    suspend fun refresh()
}
