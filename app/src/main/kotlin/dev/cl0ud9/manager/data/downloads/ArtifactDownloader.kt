package dev.cl0ud9.manager.data.downloads

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.ArtifactInfo
import dev.cl0ud9.manager.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

// download + verify pipeline, phase 3 of the spec - section 19, 42.5, 42.9
interface ArtifactDownloader {
    // artifact is explicit, not just app.latestArtifact, so a user picking an older retained
    // version from App Details' version history downloads that one instead of always the newest
    fun download(
        app: AppProfile,
        artifact: ArtifactInfo,
    ): Flow<DownloadStatus>

    // PackageInstaller copies the apk's bytes into its own session storage on commit, so the
    // downloaded file at this path is redundant once an install actually succeeds - not called on
    // failure, since a failed install's retry path reuses the same downloaded file instead of
    // re-downloading it
    fun deleteDownloadedFile(filePath: String)

    // manual purge for Settings - also catches anything deleteDownloadedFile missed (a download that
    // was never followed by a successful install: abandoned, or from a build before this existed).
    // Returns bytes freed, so the UI can confirm what actually happened.
    fun clearCache(): Long
}
