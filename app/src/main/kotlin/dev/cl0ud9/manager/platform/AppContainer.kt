package dev.cl0ud9.manager.platform

import android.content.Context
import dev.cl0ud9.manager.data.catalog.AssetCatalogRepository
import dev.cl0ud9.manager.data.settings.DataStoreSettingsRepository
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import dev.cl0ud9.manager.domain.repository.SettingsRepository

// manual DI container, kept simple for phase 1, revisit once workers/installer need injection
class AppContainer(
    context: Context,
) {
    val catalogRepository: CatalogRepository = AssetCatalogRepository(context.applicationContext)
    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context.applicationContext)
}
