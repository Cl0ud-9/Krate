package dev.cl0ud9.krate.platform.workers

import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.isVisible
import dev.cl0ud9.krate.domain.model.latestArtifact
import dev.cl0ud9.krate.domain.repository.Baseline
import dev.cl0ud9.krate.domain.repository.effectiveBaseline
import dev.cl0ud9.krate.domain.repository.forTrack
import dev.cl0ud9.krate.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.krate.platform.packageinfo.isUpdateAvailable

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
    apps.mapNotNull { app ->
        val installed = installedPackageReader.installedVersion(app.packageName)
        val recorded = baselines[app.packageName]
        app
            .takeIf { it.isVisible(hasGitHubToken) && isUpdateAvailable(installed, it, recorded) }
            ?.forTrack(effectiveBaseline(recorded, app, installed))
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
