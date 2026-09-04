package dev.cl0ud9.manager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import dev.cl0ud9.manager.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.manager.platform.packageinfo.isUpdateAvailable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    catalogRepository: CatalogRepository,
    private val installedPackageReader: InstalledPackageReader,
) : ViewModel() {
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val refreshedApps =
        combine(catalogRepository.observeApps(), refreshTrigger.onStart { emit(Unit) }) { apps, _ -> apps }

    val catalogCount: StateFlow<Int> =
        refreshedApps
            .map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    // real device-installed vs catalog-latest comparison, section 13 + 42.19 of the spec
    val pendingUpdateCount: StateFlow<Int> =
        refreshedApps
            .map { apps ->
                apps.count { app ->
                    isUpdateAvailable(installedPackageReader.installedVersion(app.packageName), app.latestVersionName)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
