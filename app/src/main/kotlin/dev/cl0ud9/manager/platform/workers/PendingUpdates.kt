package dev.cl0ud9.manager.platform.workers

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.isVisible
import dev.cl0ud9.manager.domain.model.latestVersionName
import dev.cl0ud9.manager.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.manager.platform.packageinfo.isUpdateAvailable

// pure so it can be unit tested without WorkManager/Context - the same pending-update definition
// UpdatesViewModel already uses, section 13 + 42.19 of the spec. isVisible excludes apps the
// catalog disabled and any requiresAuth app without a token - the background check should never
// notify about an update for something the user can't even see in the app right now
internal fun pendingUpdateCount(
    apps: List<AppProfile>,
    installedPackageReader: InstalledPackageReader,
    hasGitHubToken: Boolean,
): Int =
    apps.count { app ->
        app.isVisible(hasGitHubToken) &&
            isUpdateAvailable(installedPackageReader.installedVersion(app.packageName), app.latestVersionName)
    }
