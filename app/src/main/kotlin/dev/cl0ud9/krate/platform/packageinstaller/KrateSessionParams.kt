package dev.cl0ud9.krate.platform.packageinstaller

import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.SessionParams
import android.os.Build
import java.io.File

// one session's settings for every install Krate makes, in the foreground or not
internal fun krateSessionParams(
    packageName: String,
    apkFile: File,
): SessionParams {
    val params = SessionParams(SessionParams.MODE_FULL_INSTALL)
    params.setAppPackageName(packageName)
    params.setSize(apkFile.length())
    // Android 12+ skips its confirmation for an update to an app Krate installed (and that targets a recent
    // SDK); a first install, or anything else it doesn't allow, still shows the usual prompt
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        params.setRequireUserAction(SessionParams.USER_ACTION_NOT_REQUIRED)
    }
    // installed from a store, not a downloaded file, which Android 13+ answers by locking some settings away
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
    }
    // Android 14+: another store asks before replacing an app Krate first installed; ignored for an update
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        params.setRequestUpdateOwnership(true)
    }
    return params
}
