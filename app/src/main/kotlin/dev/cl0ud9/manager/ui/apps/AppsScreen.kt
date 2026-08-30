package dev.cl0ud9.manager.ui.apps

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import dev.cl0ud9.manager.ui.util.managerViewModel

// curated application catalog, section 30 of the spec
@Composable
fun AppsScreen(onAppClick: (String) -> Unit) {
    val viewModel = managerViewModel { container -> AppsViewModel(container.catalogRepository) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = uiState,
        label = "apps-content",
        transitionSpec = { fadeIn() togetherWith fadeOut() },
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
                    items(state.apps, key = { it.id }) { app ->
                        AppListItem(app = app, onClick = { onAppClick(app.id) })
                    }
                }
            }
        }
    }
}
