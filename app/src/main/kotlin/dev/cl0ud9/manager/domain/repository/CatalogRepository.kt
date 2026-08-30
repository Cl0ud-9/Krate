package dev.cl0ud9.manager.domain.repository

import dev.cl0ud9.manager.domain.model.AppProfile
import kotlinx.coroutines.flow.Flow

// backed by a bundled seed asset until manifest ingestion lands in phase 2
interface CatalogRepository {
    fun observeApps(): Flow<List<AppProfile>>

    fun observeApp(id: String): Flow<AppProfile?>
}
