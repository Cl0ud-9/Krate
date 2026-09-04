package dev.cl0ud9.manager.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.data.downloads.ArtifactDownloader
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.DownloadStatus
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppDetailsViewModel(
    catalogRepository: CatalogRepository,
    private val artifactDownloader: ArtifactDownloader,
    appId: String,
) : ViewModel() {
    val app: StateFlow<AppProfile?> =
        catalogRepository
            .observeApp(appId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val mutableDownloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = mutableDownloadStatus.asStateFlow()

    fun startDownload() {
        val currentApp = app.value ?: return
        if (mutableDownloadStatus.value is DownloadStatus.Downloading ||
            mutableDownloadStatus.value is DownloadStatus.Verifying
        ) {
            return
        }
        viewModelScope.launch {
            artifactDownloader.download(currentApp).collect { status -> mutableDownloadStatus.value = status }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
