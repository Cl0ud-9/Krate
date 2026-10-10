package dev.cl0ud9.krate.ui.details

import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.latestArtifact
import dev.cl0ud9.krate.domain.model.trackedRef
import dev.cl0ud9.krate.domain.tracked.Forge

private val GITHUB_RELEASE_URL = Regex("""^https://github\.com/([^/]+)/([^/]+)/releases/""")

// where an app's builds come from: a public repository (read off a catalog app's download link, or the one a tracked
// app was added from), or Krate's own builds
internal sealed interface PackedFrom {
    data class Repo(
        val slug: String,
        val forge: Forge = Forge.GITHUB,
    ) : PackedFrom

    data object KrateBuilds : PackedFrom
}

// builds behind a token are the ones Krate builds and signs itself; otherwise the repo in the link
internal fun packedFrom(
    downloadUrl: String?,
    requiresAuth: Boolean,
): PackedFrom? =
    when {
        requiresAuth -> PackedFrom.KrateBuilds
        downloadUrl == null -> null
        else ->
            GITHUB_RELEASE_URL
                .find(
                    downloadUrl,
                )?.let { PackedFrom.Repo("${it.groupValues[1]}/${it.groupValues[2]}") }
    }

// a tracked app's own repository, or for a catalog app the one its download link points at
internal fun sourceOf(app: AppProfile): PackedFrom? {
    val latest = app.latestArtifact
    return app.trackedRef?.let { PackedFrom.Repo(it.slug, it.forge) }
        ?: packedFrom(latest?.downloadUrl, latest?.requiresAuth == true)
}

// GitHub's own mark for GitHub; the other sites, with no mark of their own here, get a globe
internal fun siteIcon(forge: Forge?): Int =
    if (forge == null || forge == Forge.GITHUB) R.drawable.ic_github else R.drawable.ic_public_rounded
