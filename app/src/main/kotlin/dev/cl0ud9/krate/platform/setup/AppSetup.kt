package dev.cl0ud9.krate.platform.setup

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import dev.cl0ud9.krate.domain.model.SetupKind
import dev.cl0ud9.krate.domain.model.SetupStep

// whether a step is done, as far as Krate can tell from the outside
enum class StepState { DONE, TODO, UNKNOWN }

private const val FRAGMENT_ARGS_KEY = ":settings:fragment_args_key"
private const val SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
private const val MARKS_PREFS = "setup_marks"

// not in the public Settings constants, but readable the same way as the accessibility list
private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

// read straight from the system where Android allows it; usage access, all-files access and installing apps are
// another app's own business, so those are only known when Android happens to say (UNKNOWN otherwise)
fun stepState(
    context: Context,
    packageName: String,
    step: SetupStep,
): StepState =
    when (step.kind) {
        SetupKind.ACCESSIBILITY ->
            step.service?.let { serviceState(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, it) }
                ?: StepState.UNKNOWN
        SetupKind.NOTIFICATION_ACCESS ->
            step.service?.let { serviceState(context, ENABLED_NOTIFICATION_LISTENERS, it) } ?: StepState.UNKNOWN
        SetupKind.NOTIFICATIONS -> notificationState(context, packageName)
        SetupKind.BATTERY -> {
            val power = context.getSystemService(PowerManager::class.java)
            if (power?.isIgnoringBatteryOptimizations(packageName) == true) StepState.DONE else StepState.TODO
        }
        SetupKind.USAGE_ACCESS -> appOpState(context, packageName, AppOpsManager.OPSTR_GET_USAGE_STATS)
        SetupKind.ALL_FILES_ACCESS -> appOpState(context, packageName, "android:manage_external_storage")
        SetupKind.INSTALL_APPS -> appOpState(context, packageName, "android:request_install_packages")
        SetupKind.IN_APP -> StepState.UNKNOWN
    }

// the secure setting lists turned-on services as package/class separated by ':'
private fun serviceState(
    context: Context,
    setting: String,
    service: String,
): StepState {
    val wanted = ComponentName.unflattenFromString(service) ?: return StepState.UNKNOWN
    val enabled = Settings.Secure.getString(context.contentResolver, setting)
    val on = enabled.orEmpty().split(':').any { ComponentName.unflattenFromString(it) == wanted }
    return if (on) StepState.DONE else StepState.TODO
}

private fun notificationState(
    context: Context,
    packageName: String,
): StepState {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return StepState.DONE
    val granted = context.packageManager.checkPermission(Manifest.permission.POST_NOTIFICATIONS, packageName)
    return if (granted == PackageManager.PERMISSION_GRANTED) StepState.DONE else StepState.TODO
}

private fun appOpState(
    context: Context,
    packageName: String,
    op: String,
): StepState =
    runCatching {
        val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
        val mode = context.getSystemService(AppOpsManager::class.java)?.unsafeCheckOpNoThrow(op, uid, packageName)
        if (mode == AppOpsManager.MODE_ALLOWED) StepState.DONE else StepState.TODO
    }.getOrDefault(StepState.UNKNOWN)

// the system screen for the step, opened on the app itself wherever Android supports that; null for a step done in
// the app (the caller opens the app instead)
fun stepIntent(
    packageName: String,
    step: SetupStep,
): Intent? {
    val app = Uri.parse("package:$packageName")
    return when (step.kind) {
        SetupKind.ACCESSIBILITY -> accessibilityIntent(step.service)
        SetupKind.NOTIFICATION_ACCESS -> notificationAccessIntent(step.service)
        SetupKind.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, app)
        SetupKind.ALL_FILES_ACCESS -> Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, app)
        SetupKind.INSTALL_APPS -> Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, app)
        SetupKind.NOTIFICATIONS ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        // Android's own "let this app run in the background?" prompt, one tap to allow
        SetupKind.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, app)
        SetupKind.IN_APP -> null
    }
}

// the accessibility list, scrolled to (and on most phones highlighting) the app's own service
private fun accessibilityIntent(service: String?): Intent {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    val component = service?.let(ComponentName::unflattenFromString)?.flattenToString() ?: return intent
    return intent
        .putExtra(FRAGMENT_ARGS_KEY, component)
        .putExtra(SHOW_FRAGMENT_ARGS, Bundle().apply { putString(FRAGMENT_ARGS_KEY, component) })
}

// the app's own notification-access switch, or the list of them when the service isn't named
private fun notificationAccessIntent(service: String?): Intent {
    val component = service?.let(ComponentName::unflattenFromString)
    return if (component != null) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
    } else {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
}

// the app's App info page (the same one as Settings > Apps), where the ⋮ menu has "Allow restricted settings"
fun appInfoIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

// a screen to fall back to when the exact one isn't there on this phone
fun fallbackIntent(
    packageName: String,
    step: SetupStep,
): Intent =
    when (step.kind) {
        SetupKind.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        SetupKind.ALL_FILES_ACCESS -> Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        SetupKind.BATTERY -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        SetupKind.NOTIFICATION_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        else -> appInfoIntent(packageName)
    }

// Android 13+ adds "Allow restricted settings" for apps from outside an app store; Krate installs as a store now,
// so only apps it installed before (or another app did) are held back
fun restrictedSettingsApply(
    context: Context,
    packageName: String,
): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        runCatching { context.packageManager.getInstallSourceInfo(packageName).packageSource }
            .getOrNull() != PackageInstaller.PACKAGE_SOURCE_STORE

// steps Krate can't check are ticked by hand; a tick counts for the install it was made on, so a reinstall from
// scratch (which resets the app's permissions) shows them as to do again
class SetupMarks(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(MARKS_PREFS, Context.MODE_PRIVATE)
    private val packageManager = context.packageManager

    fun isMarked(
        packageName: String,
        kind: SetupKind,
    ): Boolean = installedAt(packageName)?.let { prefs.getLong(key(packageName, kind), -1L) == it } == true

    fun mark(
        packageName: String,
        kind: SetupKind,
        done: Boolean,
    ) {
        val installedAt = installedAt(packageName) ?: return
        prefs
            .edit()
            .apply {
                if (done) putLong(key(packageName, kind), installedAt) else remove(key(packageName, kind))
            }.apply()
    }

    private fun installedAt(packageName: String): Long? =
        runCatching { packageManager.getPackageInfo(packageName, 0).firstInstallTime }.getOrNull()

    private fun key(
        packageName: String,
        kind: SetupKind,
    ) = "$packageName/$kind"
}
