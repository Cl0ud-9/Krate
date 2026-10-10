package dev.cl0ud9.krate.ui.details

import androidx.compose.runtime.Composable
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.latestArtifact
import dev.cl0ud9.krate.voice.rememberInstalledLeadIn
import dev.cl0ud9.krate.voice.rememberKrateLeadIn
import dev.cl0ud9.krate.voice.updateSizeMoment

// "X is installed.", led by a milestone, the app's update count, a thank-you to its developer or a plain line
@Composable
internal fun rememberInstalledText(app: AppProfile): String =
    rememberInstalledLeadIn(app.id, app.packageName, developerOf(app), "${app.displayName} is installed.")

// "Update available: ...", led by a word on how big a step it is when the version numbers say so
@Composable
internal fun rememberUpdateAvailableText(
    installedVersion: String?,
    nextVersion: String?,
    fact: String,
): String {
    val moment = updateSizeMoment(installedVersion, nextVersion) ?: return fact
    return rememberKrateLeadIn(moment, fact, key = nextVersion)
}

// whoever publishes the app on GitHub; Krate's own builds have no one else to thank
private fun developerOf(app: AppProfile): String? {
    val repo =
        app.trackedRepo
            ?: app.latestArtifact?.let { latest ->
                (packedFrom(latest.downloadUrl, latest.requiresAuth) as? PackedFrom.Repo)?.slug
            }
    return repo?.substringBefore('/')
}
