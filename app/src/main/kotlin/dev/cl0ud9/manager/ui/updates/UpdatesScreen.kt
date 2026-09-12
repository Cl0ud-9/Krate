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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.manager.ui.components.AppListItem
import dev.cl0ud9.manager.ui.components.EmptyState
import dev.cl0ud9.manager.ui.util.RefreshOnResume
import dev.cl0ud9.manager.ui.util.StaggeredAppear
import dev.cl0ud9.manager.ui.util.managerViewModel

// pending updates with individual actions plus Update All, section 30 + 23/42.21 of the spec
@Composable
fun UpdatesScreen(onAppClick: (String) -> Unit) {
    val viewModel =
        managerViewModel { container ->
            UpdatesViewModel(container.catalogRepository, container.installedPackageReader, container.updateAllEngine)
        }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateAllState by viewModel.updateAllState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)

    AnimatedContent(
        targetState = uiState,
        label = "updates-content",
        transitionSpec = {
            fadeIn(animationSpec = tween(CONTENT_FADE_MS)) togetherWith fadeOut(animationSpec = tween(CONTENT_FADE_MS))
        },
    ) { state ->
        when (state) {
            is UpdatesUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator()
                }
            }

            is UpdatesUiState.UpToDate -> {
                EmptyState(
                    icon = Icons.Filled.CheckCircle,
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
                            onStart = viewModel::startUpdateAll,
                            onDismissResult = viewModel::dismissUpdateAllResult,
                        )
                    }

                    itemsIndexed(state.apps, key = { _, app -> app.id }) { index, app ->
                        StaggeredAppear(index = index, modifier = Modifier.animateItem()) {
                            AppListItem(
                                app = app,
                                installed = true,
                                onClick = { onAppClick(app.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val CONTENT_FADE_MS = 220
