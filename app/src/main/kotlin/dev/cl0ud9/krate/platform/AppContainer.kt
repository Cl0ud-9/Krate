package dev.cl0ud9.krate.platform

import android.content.Context
import android.os.Build
import dev.cl0ud9.krate.data.activity.DataStoreActivityLogRepository
import dev.cl0ud9.krate.data.announcements.DataStoreAnnouncementDismissalStore
import dev.cl0ud9.krate.data.auth.EncryptedGitHubCredentialStore
import dev.cl0ud9.krate.data.auth.GitHubCredentialStore
import dev.cl0ud9.krate.data.autoupdate.DataStoreAutoUpdateStore
import dev.cl0ud9.krate.data.baseline.DataStoreKrateBaselineStore
import dev.cl0ud9.krate.data.catalog.AssetCatalogRepository
import dev.cl0ud9.krate.data.catalog.CombinedCatalogRepository
import dev.cl0ud9.krate.data.catalog.PrivateCatalogSource
import dev.cl0ud9.krate.data.catalog.RemoteCatalogRepository
import dev.cl0ud9.krate.data.catalog.defaultHttpClient
import dev.cl0ud9.krate.data.downloads.AndroidDownloadProgressNotifier
import dev.cl0ud9.krate.data.downloads.ArtifactDownloader
import dev.cl0ud9.krate.data.downloads.DownloadProgressNotifier
import dev.cl0ud9.krate.data.downloads.OkHttpArtifactDownloader
import dev.cl0ud9.krate.data.settings.DataStoreSettingsRepository
import dev.cl0ud9.krate.data.tracked.ForgeReleases
import dev.cl0ud9.krate.data.tracked.TrackedAppsRepository
import dev.cl0ud9.krate.domain.installer.CleanInstallOrchestrator
import dev.cl0ud9.krate.domain.installer.InstallationEngine
import dev.cl0ud9.krate.domain.repository.ActivityLogRepository
import dev.cl0ud9.krate.domain.repository.AnnouncementDismissalStore
import dev.cl0ud9.krate.domain.repository.AutoUpdateStore
import dev.cl0ud9.krate.domain.repository.CatalogRepository
import dev.cl0ud9.krate.domain.repository.KrateBaselineStore
import dev.cl0ud9.krate.domain.repository.SettingsRepository
import dev.cl0ud9.krate.domain.updateall.UpdateAllEngine
import dev.cl0ud9.krate.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.krate.platform.packageinfo.PackageManagerInstalledPackageReader
import dev.cl0ud9.krate.platform.packageinstaller.PackageInstallerEngine
import dev.cl0ud9.krate.platform.rollback.FileRollbackStore
import dev.cl0ud9.krate.platform.rollback.RollbackStore
import dev.cl0ud9.krate.platform.selfupdate.KrateSelfUpdateInstaller
import dev.cl0ud9.krate.platform.selfupdate.KrateUpdateChecker
import dev.cl0ud9.krate.platform.selfupdate.WhatsNewTracker
import dev.cl0ud9.krate.platform.tracked.TrackedAppInspector
import dev.cl0ud9.krate.platform.work.AppWork
import dev.cl0ud9.krate.security.apk.PackageManagerApkArchiveReader
import java.io.File

// manual DI container, kept simple for phase 1, revisit once workers need injection
class AppContainer(
    context: Context,
) {
    private val seedCatalogRepository = AssetCatalogRepository(context.applicationContext)
    val githubCredentialStore: GitHubCredentialStore = EncryptedGitHubCredentialStore(context.applicationContext)
    private val catalogHttpClient = defaultHttpClient()
    val privateCatalogSource =
        PrivateCatalogSource(
            githubCredentialStore,
            catalogHttpClient,
            File(context.applicationContext.filesDir, "private-manifest-cache.json"),
        )
    private val forgeReleases = ForgeReleases(catalogHttpClient) { githubCredentialStore.getToken() }
    private val supportedAbis = Build.SUPPORTED_ABIS.toList()

    // apps someone follows from GitHub themselves, shown and updated alongside the catalog's own
    val trackedAppsRepository = TrackedAppsRepository(context.applicationContext, forgeReleases, supportedAbis)
    val catalogRepository: CatalogRepository =
        CombinedCatalogRepository(
            RemoteCatalogRepository(
                context.applicationContext,
                fallback = seedCatalogRepository,
                httpClient = catalogHttpClient,
                privateSource = privateCatalogSource,
            ),
            trackedAppsRepository,
        )
    val trackedAppInspector =
        TrackedAppInspector(
            context.applicationContext,
            forgeReleases,
            defaultHttpClient(),
            File(context.applicationContext.cacheDir, "tracked-inspect"),
            supportedAbis,
        )
    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context.applicationContext)
    val artifactDownloader: ArtifactDownloader =
        OkHttpArtifactDownloader(
            downloadsDir = File(context.applicationContext.cacheDir, "downloads"),
            archiveReader = PackageManagerApkArchiveReader(context.applicationContext),
            credentialStore = githubCredentialStore,
        )
    val downloadProgressNotifier: DownloadProgressNotifier = AndroidDownloadProgressNotifier(context.applicationContext)
    val installationEngine: InstallationEngine = PackageInstallerEngine(context.applicationContext)
    val installedPackageReader: InstalledPackageReader =
        PackageManagerInstalledPackageReader(context.applicationContext)
    val rollbackStore: RollbackStore = FileRollbackStore(context.applicationContext)
    val cleanInstallOrchestrator: CleanInstallOrchestrator = CleanInstallOrchestrator(installationEngine, rollbackStore)
    val updateAllEngine: UpdateAllEngine =
        UpdateAllEngine(artifactDownloader, installationEngine, cleanInstallOrchestrator)
    val krateUpdateChecker =
        KrateUpdateChecker(context.applicationContext, tokenProvider = { githubCredentialStore.getToken() })
    val krateSelfUpdateInstaller = KrateSelfUpdateInstaller(context.applicationContext, installationEngine)
    val whatsNewTracker = WhatsNewTracker(context.applicationContext)
    val activityLogRepository: ActivityLogRepository = DataStoreActivityLogRepository(context.applicationContext)
    val krateBaselineStore: KrateBaselineStore = DataStoreKrateBaselineStore(context.applicationContext)
    val autoUpdateStore: AutoUpdateStore = DataStoreAutoUpdateStore(context.applicationContext)

    // downloads, installs and uninstalls, kept going whether or not the app's page stays open
    val appWork =
        AppWork(
            context.applicationContext,
            artifactDownloader,
            downloadProgressNotifier,
            installationEngine,
            cleanInstallOrchestrator,
            activityLogRepository,
            krateBaselineStore,
            autoUpdateStore,
            catalogRepository,
            installedPackageReader,
        )
    val announcementDismissalStore: AnnouncementDismissalStore =
        DataStoreAnnouncementDismissalStore(context.applicationContext)
}
