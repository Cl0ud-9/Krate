package dev.cl0ud9.manager.domain.model

// download/verify pipeline states, phase 3 of the spec - installation itself lands in a later phase
sealed interface DownloadStatus {
    data object Idle : DownloadStatus

    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long?,
    ) : DownloadStatus

    data object Verifying : DownloadStatus

    data class ReadyToInstall(
        val filePath: String,
    ) : DownloadStatus

    data class Failed(
        val reason: String,
    ) : DownloadStatus
}
