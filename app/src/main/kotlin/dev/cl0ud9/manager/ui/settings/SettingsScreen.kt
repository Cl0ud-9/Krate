package dev.cl0ud9.manager.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.manager.ui.util.managerViewModel

// update-check prefs, notifications, storage, diagnostics - section 30 of the spec
// notification prefs, storage management and diagnostics land alongside the phases that need them
@Composable
fun SettingsScreen() {
    val viewModel = managerViewModel { container -> SettingsViewModel(container.settingsRepository) }
    val automaticDownloads by viewModel.automaticDownloads.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Updates", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Automatic downloads", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Download updates in the background. Installing always needs your confirmation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = automaticDownloads, onCheckedChange = viewModel::setAutomaticDownloads)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Text(text = "About", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        AppVersionRow()
    }
}

@Composable
private fun AppVersionRow() {
    val context = LocalContext.current
    val versionName =
        remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                .getOrNull() ?: "unknown"
        }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = "Version", style = MaterialTheme.typography.bodyLarge)
        Text(text = versionName, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
