package dev.cl0ud9.krate.platform.autoupdate

import android.content.Context
import android.os.Build
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.platform.appContainer
import kotlinx.coroutines.flow.first

// Android 12 is the first that lets Krate update an app without asking
val autoUpdatesSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

// on only while its downloads are too; without them there would be nothing to install
internal suspend fun autoUpdatesOn(context: Context): Boolean {
    val settings = context.appContainer().settingsRepository
    return autoUpdatesSupported &&
        settings.observeAutoInstallUpdates().first() &&
        settings.observeAutomaticDownloads().first()
}

internal suspend fun autoUpdatePolicy(
    context: Context,
    catalog: List<AppProfile>,
): AutoUpdatePolicy {
    val container = context.appContainer()
    val ledger = AutoUpdateLedger(context)
    return AutoUpdatePolicy(
        catalog = catalog,
        choices = container.autoUpdateStore.observeChoices().first(),
        tries = ledger::tries,
    ) { app ->
        AutoUpdateFacts(
            installed = container.installedPackageReader.installedVersion(app.packageName),
            krateOwnsUpdates = krateOwnsUpdates(context, app.packageName),
        )
    }
}

// what Android lists for an app put on from a file or over a cable: no store looks after those
private val NOT_A_STORE =
    setOf("com.google.android.packageinstaller", "com.android.packageinstaller", "com.android.shell")

// the name of the store Android has down as looking after an app's updates, when that's another app; null when it's
// Krate, or when nothing is on record (what's left after Krate itself is reinstalled, or an app put on from a file)
internal fun otherStoreName(
    context: Context,
    packageName: String,
): String? =
    runCatching {
        val source = context.packageManager.getInstallSourceInfo(packageName)
        val owner =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) source.updateOwnerPackageName else null
        val store =
            (owner ?: source.installingPackageName)
                ?.takeIf { it != context.packageName && it !in NOT_A_STORE }
                ?: return null
        val info = context.packageManager.getApplicationInfo(store, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrNull()

// Krate installed it, and on Android 14+ no other store has taken over its updates
internal fun krateOwnsUpdates(
    context: Context,
    packageName: String,
): Boolean =
    runCatching {
        val source = context.packageManager.getInstallSourceInfo(packageName)
        val owner =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) source.updateOwnerPackageName else null
        source.installingPackageName == context.packageName && (owner == null || owner == context.packageName)
    }.getOrDefault(false)
