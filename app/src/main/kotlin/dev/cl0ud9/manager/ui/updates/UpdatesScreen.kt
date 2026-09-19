package dev.cl0ud9.manager.ui.updates

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.platform.workers.UpdateNotifier
import dev.cl0ud9.manager.ui.components.AppListItem
import dev.cl0ud9.manager.ui.components.EmptyState
import dev.cl0ud9.manager.ui.components.ManagerPullToRefreshBox
import dev.cl0ud9.manager.ui.components.RefreshFailureSnackbar
import dev.cl0ud9.manager.ui.util.RefreshOnResume
import dev.cl0ud9.manager.ui.util.StaggeredAppear
import dev.cl0ud9.manager.ui.util.managerViewModel
import dev.cl0ud9.manager.ui.util.rememberDebouncedOnClick

// pending updates with individual actions plus Update All, section 30 + 23/42.21 of the spec
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdatesScreen(onAppClick: (String) -> Unit) {
    val viewModel =
        managerViewModel { container ->
            UpdatesViewModel(
                container.catalogRepository,
                container.installedPackageReader,
                container.updateAllEngine,
                container.activityLogRepository,
                container.githubCredentialStore,
            )
        }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateAllState by viewModel.updateAllState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)

    // Update All can take a while across several apps - if the user backgrounds the app partway
    // through, this is the one place a notification adds real value instead of just repeating
    // feedback already visible on screen (see UpdateNotifier.notifyUpdateAllResult)
    val context = LocalContext.current
    LaunchedEffect(updateAllState) {
        val finished = updateAllState as? UpdateAllUiState.Done ?: return@LaunchedEffect
        val succeeded = finished.outcomes.count { it.succeeded }
        val failed = finished.outcomes.size - succeeded
        UpdateNotifier.notifyUpdateAllResult(context, succeeded, failed)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // pending updates are the most time-sensitive data in the app - a stale manifest here
        // directly means a missed update, so this is the highest-value place for pull-to-refresh
        ManagerPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refreshFromNetwork,
            modifier = Modifier.fillMaxSize(),
        ) {
            UpdatesContent(
                uiState = uiState,
                updateAllState = updateAllState,
                viewModel = viewModel,
                onAppClick = onAppClick,
            )
        }
        RefreshFailureSnackbar(
            refreshFailed = viewModel.refreshFailed,
            message = "Couldn't refresh - showing the last known list.",
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdatesContent(
    uiState: UpdatesUiState,
    updateAllState: UpdateAllUiState,
    viewModel: UpdatesViewModel,
    onAppClick: (String) -> Unit,
) {
    AnimatedContent(
        targetState = uiState,
        label = "updates-content",
        transitionSpec = {
            fadeIn(animationSpec = tween(CONTENT_FADE_MS)) togetherWith
                fadeOut(animationSpec = tween(CONTENT_FADE_MS))
        },
    ) { state ->
        when (state) {
            is UpdatesUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }

            is UpdatesUiState.UpToDate -> {
                EmptyState(
                    icon = painterResource(R.drawable.ic_check_circle_rounded),
                    title = "You're all caught up",
                    subtitle = "Installed apps matching the catalog's latest version have nothing pending.",
                )
            }

            is UpdatesUiState.Content -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "update-all") {
                        UpdateAllBar(
                            state = updateAllState,
                            pendingCount = state.apps.size,
                            onStart = rememberDebouncedOnClick(onClick = viewModel::startUpdateAll),
                            onDismissResult = viewModel::dismissUpdateAllResult,
                        )
                    }

                    itemsIndexed(state.apps, key = { _, app -> app.id }) { index, app ->
                        StaggeredAppear(index = index, modifier = Modifier.animateItem()) {
                            AppListItem(
                                app = app,
                                installed = true,
                                onClick = rememberDebouncedOnClick(onClick = { onAppClick(app.id) }),
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val CONTENT_FADE_MS = 220
