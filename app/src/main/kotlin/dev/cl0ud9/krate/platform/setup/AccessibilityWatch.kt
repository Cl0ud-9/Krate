package dev.cl0ud9.krate.platform.setup

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.SetupKind
import dev.cl0ud9.krate.domain.model.SetupStep
import dev.cl0ud9.krate.platform.appContainer
import dev.cl0ud9.krate.platform.backup.KrateBackupService

// an accessibility service that belongs to Krate or to an app from its catalog. Some banking and payment apps refuse to
// open while any accessibility service is on, so Krate points out the ones it knows about when they're left on. A
// person's own assistive tools are never watched: they're not Krate's to question
internal data class WatchedService(
    // package/class in full, the same however Android wrote it down
    val key: String,
    val label: String,
    val own: Boolean,
    val packageName: String,
    // how to open the app's own page for it in Android's accessibility settings; null for Krate's own helper
    val step: SetupStep? = null,
)

// "pkg/.Class" and "pkg/pkg.Class" are the same service
internal fun serviceKey(flat: String): String? {
    val slash = flat.indexOf('/')
    if (slash <= 0 || slash == flat.length - 1) return null
    val pkg = flat.substring(0, slash)
    val cls = flat.substring(slash + 1)
    return "$pkg/${if (cls.startsWith(".")) pkg + cls else cls}"
}

// the secure setting lists the turned-on services separated by ':'
internal fun enabledServiceKeys(setting: String?): Set<String> =
    setting
        .orEmpty()
        .split(':')
        .mapNotNull { serviceKey(it.trim()) }
        .toSet()

// the watched services that are on and that the person hasn't said to leave on
internal fun servicesLeftOn(
    enabled: Set<String>,
    watched: List<WatchedService>,
    kept: Set<String>,
): List<WatchedService> = watched.filter { it.key in enabled && it.key !in kept }

// a service kept on is forgotten once it's off, so it's pointed out again the next time it's left on
internal fun keptWhileOn(
    enabled: Set<String>,
    kept: Set<String>,
): Set<String> = kept.filterTo(mutableSetOf()) { it in enabled }

private fun accessibilitySteps(app: AppProfile): List<SetupStep> =
    app.guide
        ?.setup
        .orEmpty()
        .filter { it.kind == SetupKind.ACCESSIBILITY && it.service != null }

internal object AccessibilityWatch {
    private const val PREFS = "accessibility_watch"
    private const val KEPT = "kept"

    private fun watched(
        context: Context,
        catalog: List<AppProfile>,
    ): List<WatchedService> {
        val reader = context.appContainer().installedPackageReader
        val own =
            serviceKey(ComponentName(context, KrateBackupService::class.java).flattenToString())?.let {
                WatchedService(it, "Krate's helper", own = true, packageName = context.packageName)
            }
        // only the few apps with an accessibility step are asked whether they're installed: that's a call to the system
        // for each, and this runs whenever Home appears
        val apps =
            catalog
                .mapNotNull { app -> accessibilitySteps(app).takeIf { it.isNotEmpty() }?.let { app to it } }
                .filter { (app, _) -> reader.installedVersion(app.packageName) != null }
                .flatMap { (app, steps) ->
                    steps.mapNotNull { step ->
                        step.service?.let(::serviceKey)?.let { key ->
                            val name = app.displayName
                            WatchedService(key, name, own = false, packageName = app.packageName, step = step)
                        }
                    }
                }
        return listOfNotNull(own) + apps
    }

    // what is on right now and wasn't told to stay on; reads the system each time, so it's never stale
    fun leftOn(
        context: Context,
        catalog: List<AppProfile>,
    ): List<WatchedService> {
        val enabled =
            enabledServiceKeys(
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            )
        val prefs = prefs(context)
        val kept = prefs.getStringSet(KEPT, emptySet()).orEmpty()
        val stillKept = keptWhileOn(enabled, kept)
        if (stillKept != kept) prefs.edit { putStringSet(KEPT, stillKept) }
        return servicesLeftOn(enabled, watched(context, catalog), stillKept)
    }

    fun keepOn(
        context: Context,
        services: List<WatchedService>,
    ) {
        val prefs = prefs(context)
        val kept = prefs.getStringSet(KEPT, emptySet()).orEmpty() + services.map { it.key }
        prefs.edit { putStringSet(KEPT, kept) }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
