package dev.cl0ud9.manager.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.manager.ui.components.EmptyState
import dev.cl0ud9.manager.ui.components.StatTile
import dev.cl0ud9.manager.ui.util.managerViewModel

// overall status, updates available, recent activity - section 30 of the spec
@Composable
fun HomeScreen() {
    val viewModel = managerViewModel { container -> HomeViewModel(container.catalogRepository) }
    val catalogCount by viewModel.catalogCount.collectAsStateWithLifecycle()
    val pendingUpdateCount by viewModel.pendingUpdateCount.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(text = "App Manager", style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(label = "Apps in catalog", value = catalogCount.toString(), modifier = Modifier.weight(1f))
            StatTile(label = "Updates pending", value = pendingUpdateCount.toString(), modifier = Modifier.weight(1f))
        }

        Text(text = "Recent activity", style = MaterialTheme.typography.titleMedium)
        EmptyState(
            icon = Icons.Filled.History,
            title = "Nothing here yet",
            subtitle = "Installs and updates you run will show up here.",
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
    }
}
