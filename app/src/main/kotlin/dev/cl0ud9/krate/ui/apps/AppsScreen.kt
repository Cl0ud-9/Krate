package dev.cl0ud9.krate.ui.apps

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.ui.components.AppListItem
import dev.cl0ud9.krate.ui.components.EmptyState
import dev.cl0ud9.krate.ui.components.KrateMessage
import dev.cl0ud9.krate.ui.components.KratePullToRefreshBox
import dev.cl0ud9.krate.ui.components.LocalNavBarClearance
import dev.cl0ud9.krate.ui.components.RefreshFailureSnackbar
import dev.cl0ud9.krate.ui.components.RefreshPillButton
import dev.cl0ud9.krate.ui.util.RefreshOnResume
import dev.cl0ud9.krate.ui.util.StaggeredAppear
import dev.cl0ud9.krate.ui.util.krateViewModel
import dev.cl0ud9.krate.ui.util.plusBottom
import dev.cl0ud9.krate.ui.util.rememberDebouncedOnClick
import dev.cl0ud9.krate.voice.KrateVoice
import dev.cl0ud9.krate.voice.Moment
import dev.cl0ud9.krate.voice.rememberKrateLine

// the tab's one ViewModel, shared by its header and its content
@Composable
fun rememberAppsViewModel(): AppsViewModel =
    krateViewModel { container ->
        AppsViewModel(
            container.catalogRepository,
            container.installedPackageReader,
            container.githubCredentialStore,
        )
    }

// the header line under "Apps"; blank while loading so the header keeps its height
@Composable
fun rememberAppsSubtitle(): String {
    val uiState by rememberAppsViewModel().uiState.collectAsStateWithLifecycle()
    return when (val state = uiState) {
        is AppsUiState.Loading -> ""
        is AppsUiState.Empty -> "Nothing on the shelves yet"
        is AppsUiState.Content -> {
            val installed = state.apps.count { it.packageName in state.installedPackageNames }
            if (installed ==
                state.apps.size
            ) {
                "All ${state.apps.size} installed"
            } else {
                "$installed of ${state.apps.size} installed"
            }
        }
    }
}

// curated application catalog, section 30 of the spec
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppsScreen(onAppClick: (String) -> Unit) {
    val viewModel = rememberAppsViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)

    Box(modifier = Modifier.fillMaxSize()) {
        // the catalog can go stale between visits (a new app added, a new version published), and this
        // is the primary list screen for it - refreshFromNetwork() re-fetches the shared manifest cache
        // rather than just re-checking local installed state, so every other screen sharing that cache
        // benefits too
        KratePullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refreshFromNetwork,
            modifier = Modifier.fillMaxSize(),
        ) {
            AppsContent(uiState = uiState, isRefreshing = isRefreshing, viewModel = viewModel, onAppClick = onAppClick)
        }
        RefreshFailureSnackbar(
            refreshFailed = viewModel.refreshFailed,
            message = {
                KrateMessage(
                    headline = KrateVoice.line(Moment.REFRESH_FAILED),
                    detail = "Couldn't refresh the catalog, so this is what Krate saw last.",
                    icon = R.drawable.ic_cloud_off_rounded,
                    actionLabel = "Retry",
                )
            },
            onRetry = viewModel::refreshFromNetwork,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppsContent(
    uiState: AppsUiState,
    isRefreshing: Boolean,
    viewModel: AppsViewModel,
    onAppClick: (String) -> Unit,
) {
    AnimatedContent(
        targetState = uiState,
        label = "apps-content",
        transitionSpec = {
            fadeIn(animationSpec = tween(CONTENT_FADE_MS)) togetherWith
                fadeOut(animationSpec = tween(CONTENT_FADE_MS))
        },
    ) { state ->
        when (state) {
            is AppsUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom = LocalNavBarClearance.current),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }

            is AppsUiState.Empty -> {
                EmptyState(
                    modifier = Modifier.padding(bottom = LocalNavBarClearance.current),
                    icon = rememberVectorPainter(Icons.Filled.Apps),
                    title = rememberKrateLine(Moment.EMPTY_CATALOG),
                    subtitle = "Apps will appear here once the catalog is populated.",
                )
            }

            is AppsUiState.Content -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp).plusBottom(LocalNavBarClearance.current),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "apps-header") {
                        AppsListHeader(
                            appCount = state.apps.size,
                            isRefreshing = isRefreshing,
                            onRefresh = viewModel::refreshFromNetwork,
                        )
                    }

                    itemsIndexed(state.apps, key = { _, app -> app.id }) { index, app ->
                        StaggeredAppear(index = index, modifier = Modifier.animateItem()) {
                            AppListItem(
                                app = app,
                                installed = app.packageName in state.installedPackageNames,
                                // a fast double-tap could otherwise reach the nav controller twice
                                // before the first navigate() call's recomposition landed, pushing
                                // App Details onto the back stack twice
                                onClick = rememberDebouncedOnClick(onClick = { onAppClick(app.id) }),
                            )
                        }
                    }
                    item(key = "suggest") {
                        StaggeredAppear(index = state.apps.size, modifier = Modifier.animateItem()) { SuggestAppCard() }
                    }
                }
            }
        }
    }
}

// the catalog's count on one side, the refresh pill on the other - always visible, not tucked away
@Composable
private fun AppsListHeader(
    appCount: Int,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (appCount == 1) "1 app in the Krate" else "$appCount apps in the Krate",
            style = MaterialTheme.typography.titleMedium,
        )
        RefreshPillButton(isRefreshing = isRefreshing, onClick = onRefresh)
    }
}

private const val CONTENT_FADE_MS = 220
