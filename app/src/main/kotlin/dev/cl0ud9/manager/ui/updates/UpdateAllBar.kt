package dev.cl0ud9.manager.ui.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.ui.components.ManagerLinearProgress
import dev.cl0ud9.manager.ui.components.SectionHeader
import dev.cl0ud9.manager.ui.theme.ShapeCache

// section 23 + 42.21 of the spec: Update All as its own card above the pending list, with a running
// status while it works and a result summary when it's done - not a silent all-or-nothing batch
@Composable
fun UpdateAllBar(
    state: UpdateAllUiState,
    pendingCount: Int,
    onStart: () -> Unit,
    onDismissResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state) {
                is UpdateAllUiState.Idle -> IdleContent(pendingCount = pendingCount, onStart = onStart)
                is UpdateAllUiState.Running -> RunningContent(state)
                is UpdateAllUiState.Done -> DoneContent(state, onDismissResult)
            }
        }
    }
}

@Composable
private fun IdleContent(
    pendingCount: Int,
    onStart: () -> Unit,
) {
    SectionHeader(
        title = "Update all",
        icon = painterResource(R.drawable.ic_system_update_alt_rounded),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )
    Text(
        text =
            if (pendingCount > 1) {
                "Updates each app in turn, dependencies first. Android asks you to confirm each one."
            } else {
                "Downloads and installs the pending update."
            },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
        Text(if (pendingCount > 1) "Update all ($pendingCount)" else "Update")
    }
}

@Composable
private fun RunningContent(state: UpdateAllUiState.Running) {
    SectionHeader(
        title = "Update all",
        icon = painterResource(R.drawable.ic_system_update_alt_rounded),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )
    Text(
        text = "${state.currentIndex + 1} of ${state.total} - ${state.currentApp.displayName}",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = state.statusLabel,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // currentIndex/total is whole-app granularity (which app we're on), not real progress within
    // it - for the common single-pending-app case that fraction is stuck at 0 for the entire run,
    // which is why this rendered as a flat, non-wavy line rather than genuinely animating.
    // Indeterminate matches every other in-progress state in the app (Installing/Verifying/
    // Uninstalling all use this same shared component) instead of a determinate bar with nothing
    // real to report
    ManagerLinearProgress(progress = null)
}

@Composable
private fun DoneContent(
    state: UpdateAllUiState.Done,
    onDismissResult: () -> Unit,
) {
    val succeeded = state.outcomes.count { it.succeeded }
    val failed = state.outcomes.size - succeeded

    val statusIconRes = if (failed == 0) R.drawable.ic_check_circle_rounded else R.drawable.ic_error_rounded
    SectionHeader(
        title = "Update all",
        icon = painterResource(statusIconRes),
        containerColor =
            if (failed == 0) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        contentColor =
            if (failed == 0) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
    )
    Text(
        text =
            if (failed == 0) {
                "All $succeeded app${if (succeeded == 1) "" else "s"} updated."
            } else {
                "$succeeded updated, $failed failed."
            },
        style = MaterialTheme.typography.bodyMedium,
    )
    if (failed > 0) {
        state.outcomes.filter { !it.succeeded }.forEach { outcome ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    painterResource(R.drawable.ic_error_rounded),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.height(14.dp),
                )
                Text(
                    text = "${outcome.app.displayName}: ${outcome.reason ?: "failed"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    TextButton(onClick = onDismissResult) {
        Text("Dismiss")
    }
}
