package dev.cl0ud9.krate.domain.repository

import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.ArtifactInfo
import dev.cl0ud9.krate.domain.model.latestArtifact
import kotlinx.coroutines.flow.Flow

// the user's per-app say over automatic updates
data class AutoUpdateChoices(
    // apps kept on updating by hand
    val manual: Set<String> = emptySet(),
    // app id to the build it should never install by itself; a newer build isn't skipped
    val skipped: Map<String, String> = emptyMap(),
) {
    fun isSkipped(
        appId: String,
        artifact: ArtifactInfo,
    ): Boolean = skipped[appId] == artifact.buildKey
}

interface AutoUpdateStore {
    fun observeChoices(): Flow<AutoUpdateChoices>

    suspend fun setManual(
        appId: String,
        manual: Boolean,
    )

    // null takes the skip back
    suspend fun skip(
        appId: String,
        buildKey: String?,
    )
}

// tells two builds apart even when they share a version name (a rebuild with newer patches)
val ArtifactInfo.buildKey: String
    get() = buildId ?: versionName

// the newest build of the same theme when an older one was picked on purpose (a rollback), which automatic updates
// then leave alone
fun AppProfile.newerThanPicked(picked: ArtifactInfo): ArtifactInfo? {
    val baseline = Baseline(picked.versionName, picked.buildId, picked.label)
    val track = forTrack(baseline)
    return track.latestArtifact?.takeIf { it.isNewerThan(baseline, track.artifacts) }
}
