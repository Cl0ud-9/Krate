package dev.cl0ud9.manager.data.downloads

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

// download + verify pipeline, phase 3 of the spec - section 19, 42.5, 42.9
interface ArtifactDownloader {
    fun download(app: AppProfile): Flow<DownloadStatus>
}
