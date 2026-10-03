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
