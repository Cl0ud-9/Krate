package dev.cl0ud9.manager.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.data.downloads.ArtifactDownloader
import dev.cl0ud9.manager.domain.installer.InstallationEngine
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.DownloadStatus
import dev.cl0ud9.manager.domain.model.InstallStatus
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import dev.cl0ud9.manager.platform.packageinfo.InstalledPackageReader
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class AppDetailsViewModel(
    catalogRepository: CatalogRepository,
    private val artifactDownloader: ArtifactDownloader,
    private val installationEngine: InstallationEngine,
    private val installedPackageReader: InstalledPackageReader,
    appId: String,
) : ViewModel() {
    val app: StateFlow<AppProfile?> =
        catalogRepository
            .observeApp(appId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    // installed state is device-local, so a resume-triggered refresh() re-checks it - a successful
    // install also refreshes immediately below, section 13 + 42.19 of the spec
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val installedVersionName: StateFlow<String?> =
        combine(app, refreshTrigger.onStart { emit(Unit) }) { profile, _ -> profile }
            .map { profile -> profile?.let { installedPackageReader.installedVersion(it.packageName)?.versionName } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val mutableDownloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = mutableDownloadStatus.asStateFlow()

    private val mutableInstallStatus = MutableStateFlow<InstallStatus>(InstallStatus.Idle)
    val installStatus: StateFlow<InstallStatus> = mutableInstallStatus.asStateFlow()

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

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

    fun startInstall() {
        val currentApp = app.value
        val readyStatus = mutableDownloadStatus.value as? DownloadStatus.ReadyToInstall
        val busy =
            mutableInstallStatus.value is InstallStatus.Installing ||
                mutableInstallStatus.value is InstallStatus.WaitingForUser
        if (currentApp == null || readyStatus == null || busy) return
        viewModelScope.launch {
            installationEngine.install(currentApp, File(readyStatus.filePath)).collect { status ->
                mutableInstallStatus.value = status
                if (status is InstallStatus.Success) refresh()
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
