package dev.cl0ud9.manager.platform

import android.content.Context
import dev.cl0ud9.manager.data.catalog.AssetCatalogRepository
import dev.cl0ud9.manager.data.catalog.RemoteCatalogRepository
import dev.cl0ud9.manager.data.downloads.ArtifactDownloader
import dev.cl0ud9.manager.data.downloads.OkHttpArtifactDownloader
import dev.cl0ud9.manager.data.settings.DataStoreSettingsRepository
import dev.cl0ud9.manager.domain.installer.InstallationEngine
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import dev.cl0ud9.manager.domain.repository.SettingsRepository
import dev.cl0ud9.manager.platform.packageinstaller.PackageInstallerEngine
import dev.cl0ud9.manager.security.apk.PackageManagerApkArchiveReader
import java.io.File

// manual DI container, kept simple for phase 1, revisit once workers need injection
class AppContainer(
    context: Context,
) {
    private val seedCatalogRepository = AssetCatalogRepository(context.applicationContext)
    val catalogRepository: CatalogRepository =
        RemoteCatalogRepository(context.applicationContext, fallback = seedCatalogRepository)
    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context.applicationContext)
    val artifactDownloader: ArtifactDownloader =
        OkHttpArtifactDownloader(
            downloadsDir = File(context.applicationContext.cacheDir, "downloads"),
            archiveReader = PackageManagerApkArchiveReader(context.applicationContext),
        )
    val installationEngine: InstallationEngine = PackageInstallerEngine(context.applicationContext)
}
