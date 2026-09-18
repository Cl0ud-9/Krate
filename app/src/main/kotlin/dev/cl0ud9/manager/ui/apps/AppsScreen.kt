package dev.cl0ud9.manager.ui.apps

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.manager.ui.components.AppListItem
import dev.cl0ud9.manager.ui.components.EmptyState
import dev.cl0ud9.manager.ui.components.ManagerPullToRefreshBox
import dev.cl0ud9.manager.ui.components.RefreshPillButton
import dev.cl0ud9.manager.ui.util.RefreshOnResume
import dev.cl0ud9.manager.ui.util.StaggeredAppear
import dev.cl0ud9.manager.ui.util.managerViewModel

// curated application catalog, section 30 of the spec
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppsScreen(onAppClick: (String) -> Unit) {
    val viewModel =
        managerViewModel { container ->
            AppsViewModel(
                container.catalogRepository,
                container.installedPackageReader,
                container.githubCredentialStore,
            )
        }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)

    // the catalog can go stale between visits (a new app added, a new version published), and this is
    // the primary list screen for it - refreshFromNetwork() re-fetches the shared manifest cache rather
    // than just re-checking local installed state, so every other screen sharing that cache benefits too
    ManagerPullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refreshFromNetwork,
        modifier = Modifier.fillMaxSize(),
    ) {
        AppsContent(uiState = uiState, isRefreshing = isRefreshing, viewModel = viewModel, onAppClick = onAppClick)
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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }

            is AppsUiState.Empty -> {
                EmptyState(
                    icon = Icons.Filled.Apps,
                    title = "No apps in the catalog yet",
                    subtitle = "Curated apps will appear here once the catalog is populated.",
                )
            }

            is AppsUiState.Content -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
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
                                onClick = { onAppClick(app.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// the catalog's count on one side, the refresh pill on the other - persistently visible, a
// count/label plus a pill action in one header row, not tucked away
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
            text = if (appCount == 1) "1 app" else "$appCount apps",
            style = MaterialTheme.typography.titleMedium,
        )
        RefreshPillButton(isRefreshing = isRefreshing, onClick = onRefresh)
    }
}

private const val CONTENT_FADE_MS = 220
