package dev.cl0ud9.manager.ui.updates

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import dev.cl0ud9.manager.ui.components.EmptyState

// pending updates with individual actions and Update All - section 30 of the spec
// update detection lands with manifest ingestion in phase 2, so this is genuinely empty for now
@Composable
fun UpdatesScreen() {
    EmptyState(
        icon = Icons.Filled.CheckCircle,
        title = "You're all caught up",
        subtitle = "Update checks will appear here once release detection is wired up.",
    )
}
