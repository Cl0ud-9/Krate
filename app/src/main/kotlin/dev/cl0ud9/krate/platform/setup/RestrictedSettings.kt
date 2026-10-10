package dev.cl0ud9.krate.platform.setup

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build

// what to do when Android greys out a switch for an app it holds back
const val RESTRICTED_SETTINGS_HELP =
    "Android holds some switches back for apps that didn't come from the Play Store. Try the switch once first: " +
        "Android only offers the way past it after that. Then open App info, tap ⋮ at the top right (on some phones " +
        "it's at the bottom of the page instead), choose Allow restricted settings, and come back to turn it on."

private const val PLAY_STORE = "com.android.vending"

// Android 13 and 14 hold back apps that didn't come from an app store, and Krate installs as one. Android 15 and newer
// only trust installers on the phone maker's list, so there anything not from the Play Store can be held back
fun restrictedSettingsApply(
    context: Context,
    packageName: String,
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val source = runCatching { context.packageManager.getInstallSourceInfo(packageName) }.getOrNull()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        source?.installingPackageName != PLAY_STORE
    } else {
        source?.packageSource != PackageInstaller.PACKAGE_SOURCE_STORE
    }
}
