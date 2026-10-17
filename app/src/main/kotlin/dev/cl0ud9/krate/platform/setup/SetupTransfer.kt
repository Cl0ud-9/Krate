package dev.cl0ud9.krate.platform.setup

import dev.cl0ud9.krate.data.tracked.TrackedAppsRepository
import dev.cl0ud9.krate.data.tracked.TrackedEntry
import dev.cl0ud9.krate.data.tracked.trackedIdFor
import dev.cl0ud9.krate.domain.model.LaunchTab
import dev.cl0ud9.krate.domain.model.NavBarStyle
import dev.cl0ud9.krate.domain.model.ThemeMode
import dev.cl0ud9.krate.domain.repository.AutoUpdateStore
import dev.cl0ud9.krate.domain.repository.CatalogRepository
import dev.cl0ud9.krate.domain.repository.SettingsRepository
import dev.cl0ud9.krate.domain.setup.KrateSetup
import dev.cl0ud9.krate.domain.setup.SetupSettings
import dev.cl0ud9.krate.domain.setup.SetupTracked
import dev.cl0ud9.krate.domain.setup.key
import dev.cl0ud9.krate.domain.tracked.Forge
import kotlinx.coroutines.flow.first

// what applying a setup did, so the person is told
data class SetupApplied(
    val trackedAdded: Int,
    // already followed, or an app the catalog already carries
    val trackedSkipped: Int,
    val manualSet: Int,
    val settingsSet: Int,
)

// what loading a file would do with its tracked apps
data class SetupPreview(
    val toAdd: List<SetupTracked>,
    val alreadyHere: Int,
)

// reads Krate's current setup into a file's worth, and puts a setup read from a file back. The stores are the same ones
// the screens use, so nothing here can reach anything they can't: no token, no app settings, nothing about the phone
class SetupTransfer(
    private val tracked: TrackedAppsRepository,
    private val settings: SettingsRepository,
    private val autoUpdates: AutoUpdateStore,
    private val catalog: CatalogRepository,
) {
    suspend fun current(nowMillis: Long): KrateSetup =
        KrateSetup(
            exportedAtMillis = nowMillis,
            tracked = tracked.entries().map { it.toSetup() },
            manualUpdates =
                autoUpdates
                    .observeChoices()
                    .first()
                    .manual
                    .sorted(),
            settings = currentSettings(),
        )

    // which of a file's tracked apps would be added and how many are already here (followed, or carried by the
    // catalog), by the rule applying uses, so what is said before matches what is done
    suspend fun preview(setup: KrateSetup): SetupPreview {
        val have = tracked.entries().map { it.toSetup().key() }.toSet()
        val listed = catalog.observeApps().first().filter { it.trackedRepo == null }
        val inCatalog = listed.mapTo(mutableSetOf()) { it.packageName }
        val (add, skip) = setup.tracked.partition { it.key() !in have && it.packageName !in inCatalog }
        return SetupPreview(add, skip.size)
    }

    suspend fun apply(setup: KrateSetup): SetupApplied {
        val preview = preview(setup)
        preview.toAdd.forEach { tracked.add(it.toEntry()) }
        setup.manualUpdates.forEach { autoUpdates.setManual(it, manual = true) }
        val set = applySettings(setup.settings)
        return SetupApplied(preview.toAdd.size, preview.alreadyHere, setup.manualUpdates.size, set)
    }

    private suspend fun currentSettings() =
        SetupSettings(
            themeMode = settings.observeThemeMode().first().name,
            navBarStyle = settings.observeNavBarStyle().first().name,
            navBarCornerRadius = settings.observeNavBarCornerRadius().first(),
            navBarCompactMode = settings.observeNavBarCompactMode().first(),
            liquidGlass = settings.observeLiquidGlass().first(),
            defaultLaunchTab = settings.observeDefaultLaunchTab().first().name,
            automaticDownloads = settings.observeAutomaticDownloads().first(),
            downloadOnMobileData = settings.observeDownloadOnMobileData().first(),
            autoInstallUpdates = settings.observeAutoInstallUpdates().first(),
        )

    // each one the file carries, set the way the screens set it; how many were set is what's reported
    private suspend fun applySettings(incoming: SetupSettings): Int {
        var set = 0
        incoming.themeMode?.let { name ->
            settings.setThemeMode(ThemeMode.valueOf(name))
            set++
        }
        incoming.navBarStyle?.let { name ->
            settings.setNavBarStyle(NavBarStyle.valueOf(name))
            set++
        }
        incoming.navBarCornerRadius?.let {
            settings.setNavBarCornerRadius(it)
            set++
        }
        incoming.navBarCompactMode?.let {
            settings.setNavBarCompactMode(it)
            set++
        }
        incoming.liquidGlass?.let {
            settings.setLiquidGlass(it)
            set++
        }
        incoming.defaultLaunchTab?.let { name ->
            settings.setDefaultLaunchTab(LaunchTab.valueOf(name))
            set++
        }
        return set + applyDownloadSettings(incoming)
    }

    private suspend fun applyDownloadSettings(incoming: SetupSettings): Int {
        var set = 0
        incoming.automaticDownloads?.let {
            settings.setAutomaticDownloads(it)
            set++
        }
        incoming.downloadOnMobileData?.let {
            settings.setDownloadOnMobileData(it)
            set++
        }
        incoming.autoInstallUpdates?.let {
            settings.setAutoInstallUpdates(it)
            set++
        }
        return set
    }
}

private fun TrackedEntry.toSetup() =
    SetupTracked(
        forge = forge,
        owner = owner,
        repo = repo,
        packageName = packageName,
        displayName = displayName,
        certificateSha256 = certificateSha256,
        fileShape = fileShape,
        includePrerelease = includePrerelease,
    )

// followed again from the repository: its builds and icon come with the next check, and its id is worked out here
// rather than taken from the file
private fun SetupTracked.toEntry(): TrackedEntry {
    val ref =
        dev.cl0ud9.krate.domain.tracked
            .RepoRef(owner, repo, Forge.fromId(forge))
    return TrackedEntry(
        id = trackedIdFor(ref),
        owner = owner,
        repo = repo,
        packageName = packageName,
        displayName = displayName,
        certificateSha256 = certificateSha256.lowercase(),
        fileShape = fileShape,
        includePrerelease = includePrerelease,
        addedAtMillis = System.currentTimeMillis(),
        forge = forge,
    )
}
