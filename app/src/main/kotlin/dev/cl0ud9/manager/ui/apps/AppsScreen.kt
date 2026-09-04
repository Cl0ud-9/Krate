package dev.cl0ud9.manager.ui.apps

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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.CircularProgressIndicator
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

// curated application catalog, section 30 of the spec
@Composable
fun AppsScreen(onAppClick: (String) -> Unit) {
    val viewModel =
        managerViewModel { container -> AppsViewModel(container.catalogRepository, container.installedPackageReader) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RefreshOnResume(viewModel::refresh)

    AnimatedContent(
        targetState = uiState,
        label = "apps-content",
        transitionSpec = {
            fadeIn(animationSpec = tween(CONTENT_FADE_MS)) togetherWith fadeOut(animationSpec = tween(CONTENT_FADE_MS))
        },
    ) { state ->
        when (state) {
            is AppsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
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

private const val CONTENT_FADE_MS = 220
