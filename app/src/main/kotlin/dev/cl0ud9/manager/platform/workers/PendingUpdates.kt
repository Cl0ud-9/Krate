package dev.cl0ud9.manager.platform.workers

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.isVisible
import dev.cl0ud9.manager.domain.model.latestArtifact
import dev.cl0ud9.manager.domain.repository.Baseline
import dev.cl0ud9.manager.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.manager.platform.packageinfo.isUpdateAvailable

// pure so it can be unit tested without WorkManager/Context - the same pending-update definition
// UpdatesViewModel already uses, section 13 + 42.19 of the spec. isVisible excludes apps the
// catalog disabled and any requiresAuth app without a token - the background check should never
// notify about an update for something the user can't even see in the app right now
internal fun pendingUpdates(
    apps: List<AppProfile>,
    installedPackageReader: InstalledPackageReader,
    hasGitHubToken: Boolean,
    baselines: Map<String, Baseline>,
): List<AppProfile> =
    apps.filter { app ->
        app.isVisible(hasGitHubToken) &&
            isUpdateAvailable(installedPackageReader.installedVersion(app.packageName), app, baselines[app.packageName])
    }

internal fun pendingUpdateCount(
    apps: List<AppProfile>,
    installedPackageReader: InstalledPackageReader,
    hasGitHubToken: Boolean,
    baselines: Map<String, Baseline>,
): Int = pendingUpdates(apps, installedPackageReader, hasGitHubToken, baselines).size

// identifies exactly which builds are pending - the notification only fires again when this
// changes, not on every periodic check that finds the same updates still waiting
internal fun pendingUpdatesSignature(pending: List<AppProfile>): String =
    pending
        .map { app -> "${app.id}:${app.latestArtifact?.let { it.buildId ?: it.versionName }}" }
        .sorted()
        .joinToString(",")
