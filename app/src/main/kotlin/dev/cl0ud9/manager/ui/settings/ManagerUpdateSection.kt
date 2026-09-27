package dev.cl0ud9.manager.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.platform.selfupdate.ManagerUpdateStatus
import dev.cl0ud9.manager.ui.components.BusyButtonContent
import dev.cl0ud9.manager.ui.components.fadeThrough
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.util.DebouncedButtonState
import dev.cl0ud9.manager.voice.Moment
import dev.cl0ud9.manager.voice.rememberKrateLine

// the status line fades through its states while the button stays put, so a check never resizes the card
@Composable
internal fun ManagerUpdateSection(
    state: ManagerUpdateUiState,
    actions: ManagerUpdateActions,
) {
    val available = ((state as? ManagerUpdateUiState.Result)?.status as? ManagerUpdateStatus.UpdateAvailable)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedContent(
            targetState = state,
            transitionSpec = fadeThrough(),
            contentAlignment = Alignment.CenterStart,
            label = "manager-update",
            modifier = Modifier.fillMaxWidth(),
        ) { shown -> ManagerUpdateStatusLine(state = shown) }
        if (available != null) {
            SelfUpdateAction(
                status = available,
                selfUpdateState = actions.selfUpdateState,
                onInstallUpdate = actions.onInstallUpdate,
            )
        } else {
            CheckForUpdatesButton(state = state, button = actions.checkForUpdateState)
        }
    }
}

@Composable
private fun ManagerUpdateStatusLine(state: ManagerUpdateUiState) {
    val colors = MaterialTheme.colorScheme
    when (state) {
        is ManagerUpdateUiState.Idle ->
            ManagerUpdateStatusRow(
                icon = painterResource(R.drawable.ic_system_update_alt_rounded),
                badgeColor = colors.surfaceContainerHighest,
                contentColor = colors.onSurfaceVariant,
                text = "Check for a newer version of Krate.",
            )

        is ManagerUpdateUiState.Checking ->
            ManagerUpdateStatusRow(
                icon = painterResource(R.drawable.ic_system_update_alt_rounded),
                badgeColor = colors.surfaceContainerHighest,
                contentColor = colors.onSurfaceVariant,
                text = rememberKrateLine(Moment.CHECKING_FOR_UPDATES),
            )

        is ManagerUpdateUiState.Result -> ManagerUpdateResultLine(status = state.status)
    }
}

@Composable
private fun ManagerUpdateResultLine(status: ManagerUpdateStatus) {
    val colors = MaterialTheme.colorScheme
    when (status) {
        is ManagerUpdateStatus.UpToDate ->
            ManagerUpdateStatusRow(
                icon = painterResource(R.drawable.ic_check_circle_rounded),
                badgeColor = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                text = "${rememberKrateLine(Moment.KRATE_LATEST)} You're on the latest version.",
            )

        is ManagerUpdateStatus.UpdateAvailable ->
            ManagerUpdateStatusRow(
                icon = painterResource(R.drawable.ic_system_update_alt_rounded),
                badgeColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
                text = "Version ${status.latestVersion} is available.",
                emphasize = true,
            )

        is ManagerUpdateStatus.NoReleasePublished ->
            ManagerUpdateStatusRow(
                icon = painterResource(R.drawable.ic_info_rounded),
                badgeColor = colors.surfaceContainerHighest,
                contentColor = colors.onSurfaceVariant,
                text = "No Krate releases have been published yet.",
            )

        is ManagerUpdateStatus.Failed ->
            ManagerUpdateStatusRow(
                icon = painterResource(R.drawable.ic_error_rounded),
                badgeColor = colors.errorContainer,
                contentColor = colors.onErrorContainer,
                text = status.reason,
            )
    }
}

// one button until an update is found; a tap mid-check is ignored rather than greying the button out
@Composable
private fun CheckForUpdatesButton(
    state: ManagerUpdateUiState,
    button: DebouncedButtonState,
) {
    val checking = state is ManagerUpdateUiState.Checking
    val label =
        when {
            state is ManagerUpdateUiState.Idle -> "Check for updates"
            (state as? ManagerUpdateUiState.Result)?.status is ManagerUpdateStatus.Failed -> "Retry"
            else -> "Check again"
        }
    FilledTonalButton(
        onClick = { if (!checking && button.enabled) button.onClick() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        BusyButtonContent(busy = checking, text = label)
    }
}

// internal, not private - also called from ManagerSelfUpdateContent.kt (same package)
@Composable
internal fun ManagerUpdateStatusRow(
    icon: Painter,
    badgeColor: Color,
    contentColor: Color,
    text: String,
    emphasize: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(36.dp).clip(ShapeCache.smooth12).background(badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        }
        Text(
            text = text,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
