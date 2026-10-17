package dev.cl0ud9.krate.platform.autoupdate

import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.repository.AutoUpdateStore
import dev.cl0ud9.krate.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

// what's decided for every app at once: whether automatic updates are on in Settings (downloads too, or there would be
// nothing to install), and the apps the person keeps on updating by hand
internal data class AutoUpdateGlobal(
    val on: Boolean,
    val manual: Set<String>,
    // when a pause ends; 0 for none. A pause that has passed is the same as none
    val pausedUntil: Long = 0L,
)

internal enum class UpdateMode { AUTOMATIC, PAUSED, BY_HAND }

// how an installed app gets its updates, or null when there's nothing to say: automatic updates are off or not possible
// on this phone, or the app isn't installed. The same rule the app's own card follows
internal fun updateMode(
    global: AutoUpdateGlobal,
    app: AppProfile,
    installed: Boolean,
    krateOwns: Boolean,
    nowMillis: Long = System.currentTimeMillis(),
): UpdateMode? {
    if (!global.on || !installed) return null
    val automatic =
        app.id !in global.manual && app.installationMode != InstallationMode.CLEAN_INSTALL && krateOwns
    return when {
        !automatic -> UpdateMode.BY_HAND
        isPausedAt(global.pausedUntil, nowMillis) -> UpdateMode.PAUSED
        else -> UpdateMode.AUTOMATIC
    }
}

// one shared reading of the settings behind it, so every row and page that shows the mark agrees without each
// watching the settings themselves
internal class AutoUpdateStanding(
    settings: SettingsRepository,
    store: AutoUpdateStore,
) {
    val global: StateFlow<AutoUpdateGlobal> =
        combine(
            settings.observeAutoInstallUpdates(),
            settings.observeAutomaticDownloads(),
            store.observeChoices(),
            settings.observeAutoUpdatePausedUntil(),
        ) { autoInstall, downloads, choices, pausedUntil ->
            AutoUpdateGlobal(autoUpdatesSupported && autoInstall && downloads, choices.manual, pausedUntil)
        }.stateIn(
            CoroutineScope(SupervisorJob() + Dispatchers.Default),
            SharingStarted.Eagerly,
            AutoUpdateGlobal(on = false, manual = emptySet()),
        )
}
