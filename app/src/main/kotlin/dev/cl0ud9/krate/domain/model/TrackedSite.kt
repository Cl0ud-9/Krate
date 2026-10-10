package dev.cl0ud9.krate.domain.model

import dev.cl0ud9.krate.domain.tracked.Forge
import dev.cl0ud9.krate.domain.tracked.RepoRef

// where a tracked app lives, as a repository reference; null for the catalog's own apps
val AppProfile.trackedRef: RepoRef?
    get() =
        trackedRepo?.let { slug ->
            RepoRef(slug.substringBeforeLast('/'), slug.substringAfterLast('/'), Forge.fromId(trackedForge))
        }

// from GitHub, or not tracked at all: GitHub's own mark fits
val AppProfile.fromGitHub: Boolean
    get() = Forge.fromId(trackedForge) == Forge.GITHUB
