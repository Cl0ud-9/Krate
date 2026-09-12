package dev.cl0ud9.manager.platform.workers

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.manager.platform.packageinfo.isUpdateAvailable

// pure so it can be unit tested without WorkManager/Context - the same pending-update definition
// UpdatesViewModel already uses, section 13 + 42.19 of the spec
internal fun pendingUpdateCount(
    apps: List<AppProfile>,
    installedPackageReader: InstalledPackageReader,
): Int =
    apps.count { app ->
        isUpdateAvailable(installedPackageReader.installedVersion(app.packageName), app.latestVersionName)
    }
