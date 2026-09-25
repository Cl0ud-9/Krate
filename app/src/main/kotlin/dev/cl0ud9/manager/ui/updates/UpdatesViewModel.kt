package dev.cl0ud9.manager.ui.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cl0ud9.manager.data.auth.GitHubCredentialStore
import dev.cl0ud9.manager.domain.model.ActivityAction
import dev.cl0ud9.manager.domain.model.ActivityEntry
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.isVisible
import dev.cl0ud9.manager.domain.model.latestArtifact
import dev.cl0ud9.manager.domain.repository.ActivityLogRepository
import dev.cl0ud9.manager.domain.repository.Baseline
import dev.cl0ud9.manager.domain.repository.CatalogRepository
import dev.cl0ud9.manager.domain.repository.ManagerBaselineStore
import dev.cl0ud9.manager.domain.updateall.UpdateAllEngine
import dev.cl0ud9.manager.domain.updateall.UpdateAllOutcome
import dev.cl0ud9.manager.domain.updateall.UpdateAllPlanner
import dev.cl0ud9.manager.domain.updateall.UpdateAllProgress
import dev.cl0ud9.manager.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.manager.platform.packageinfo.isUpdateAvailable
import dev.cl0ud9.manager.ui.util.withMinimumDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface UpdatesUiState {
    data object Loading : UpdatesUiState

    data object UpToDate : UpdatesUiState

    data class Content(
        val apps: List<AppProfile>,
    ) : UpdatesUiState
}

sealed interface UpdateAllUiState {
    data object Idle : UpdateAllUiState

    data class Running(
        val currentApp: AppProfile,
        val currentIndex: Int,
        val total: Int,
        val statusLabel: String,
    ) : UpdateAllUiState

    data class Done(
        val outcomes: List<UpdateAllOutcome>,
    ) : UpdateAllUiState
}

// apps whose installed version genuinely differs from the catalog's latest, section 13 + 42.19 of the spec.
// installed state is device-local, so a resume-triggered refresh() re-checks it after an install/uninstall
class UpdatesViewModel(
    private val catalogRepository: CatalogRepository,
    private val installedPackageReader: InstalledPackageReader,
    private val updateAllEngine: UpdateAllEngine,
    private val activityLogRepository: ActivityLogRepository,
    private val githubCredentialStore: GitHubCredentialStore,
    private val managerBaselineStore: ManagerBaselineStore,
) : ViewModel() {
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val mutableIsRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = mutableIsRefreshing.asStateFlow()

    // one-shot: a failed pull-to-refresh used to just stop the spinner with zero feedback - see
    // AppsViewModel's identical field for why this doesn't mean the list itself ever goes empty
    private val mutableRefreshFailed = MutableSharedFlow<Unit>()
    val refreshFailed: SharedFlow<Unit> = mutableRefreshFailed.asSharedFlow()

    private val catalog: StateFlow<List<AppProfile>> =
        catalogRepository
            .observeApps()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    // toUiState() calls installedPackageReader.installedVersion() (a real PackageManager Binder
    // call) once per catalog app - flowOn(IO) keeps that off the main thread, same reasoning as
    // AppsViewModel's identical fix
    val uiState: StateFlow<UpdatesUiState> =
        combine(
            catalog,
            refreshTrigger.onStart { emit(Unit) },
            managerBaselineStore.observeBaselines(),
        ) { apps, _, baselines -> toUiState(apps, baselines) }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UpdatesUiState.Loading)

    private val mutableUpdateAllState = MutableStateFlow<UpdateAllUiState>(UpdateAllUiState.Idle)
    val updateAllState: StateFlow<UpdateAllUiState> = mutableUpdateAllState.asStateFlow()

    fun refresh() {
        refreshTrigger.tryEmit(Unit)
    }

    // pull-to-refresh: the one path that actually re-fetches the manifest over the network (see
    // RemoteCatalogRepository) - guarded so a second pull while one is already in flight is a no-op
    // rather than a duplicate request
    fun refreshFromNetwork() {
        if (mutableIsRefreshing.value) return
        viewModelScope.launch {
            mutableIsRefreshing.value = true
            val result = withMinimumDuration { runCatching { catalogRepository.refresh() } }
            mutableIsRefreshing.value = false
            if (result.isFailure) mutableRefreshFailed.emit(Unit)
        }
    }

    // section 23 + 42.21 of the spec: dependency-ordered sequential deployment across every pending
    // update, with a running result summary rather than an all-or-nothing batch
    fun startUpdateAll() {
        val pending = (uiState.value as? UpdatesUiState.Content)?.apps ?: return
        if (mutableUpdateAllState.value is UpdateAllUiState.Running) return

        val ordered = UpdateAllPlanner.order(pending, catalog.value)
        viewModelScope.launch {
            updateAllEngine.run(ordered).collect { progress ->
                mutableUpdateAllState.value =
                    when (progress) {
                        is UpdateAllProgress.Step ->
                            UpdateAllUiState.Running(
                                currentApp = progress.currentApp,
                                currentIndex = progress.currentIndex,
                                total = progress.total,
                                statusLabel = progress.statusLabel,
                            )

                        is UpdateAllProgress.Finished -> {
                            recordActivity(progress.outcomes)
                            UpdateAllUiState.Done(progress.outcomes)
                        }
                    }
            }
            refresh()
        }
    }

    // Update All only ever targets apps that already have a pending update (see toUiState below),
    // so every successful outcome here is genuinely an UPDATED event, never a fresh install. Also
    // records the version actually installed as this app's new manager baseline - Update All always
    // installs an app's latestArtifact, so that's what just became true on the device
    private suspend fun recordActivity(outcomes: List<UpdateAllOutcome>) {
        outcomes.filter { it.succeeded }.forEach { outcome ->
            activityLogRepository.record(
                ActivityEntry(
                    id = UUID.randomUUID().toString(),
                    appId = outcome.app.id,
                    appName = outcome.app.displayName,
                    action = ActivityAction.UPDATED,
                    timestampMillis = System.currentTimeMillis(),
                ),
            )
            outcome.app.latestArtifact?.let { artifact ->
                managerBaselineStore.recordInstall(outcome.app.packageName, artifact)
            }
        }
    }

    fun dismissUpdateAllResult() {
        mutableUpdateAllState.value = UpdateAllUiState.Idle
    }

    private fun toUiState(
        apps: List<AppProfile>,
        baselines: Map<String, Baseline>,
    ): UpdatesUiState {
        val hasToken = githubCredentialStore.getToken() != null
        val pending =
            apps
                .filter { it.isVisible(hasToken) }
                .filter { app ->
                    isUpdateAvailable(
                        installedPackageReader.installedVersion(app.packageName),
                        app,
                        baselines[app.packageName],
                    )
                }
        return if (pending.isEmpty()) UpdatesUiState.UpToDate else UpdatesUiState.Content(pending)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
