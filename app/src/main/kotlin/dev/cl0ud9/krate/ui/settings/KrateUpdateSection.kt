package dev.cl0ud9.krate.ui.settings

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
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.platform.selfupdate.KrateUpdateStatus
import dev.cl0ud9.krate.ui.components.BusyButtonContent
import dev.cl0ud9.krate.ui.components.fadeThrough
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.DebouncedButtonState
import dev.cl0ud9.krate.voice.Moment
import dev.cl0ud9.krate.voice.rememberKrateLeadIn
import dev.cl0ud9.krate.voice.rememberKrateLine

// the status line fades through its states while the button stays put, so a check never resizes the card
@Composable
internal fun KrateUpdateSection(
    state: KrateUpdateUiState,
    actions: KrateUpdateActions,
) {
    val available = ((state as? KrateUpdateUiState.Result)?.status as? KrateUpdateStatus.UpdateAvailable)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedContent(
            targetState = state,
            transitionSpec = fadeThrough(),
            contentAlignment = Alignment.CenterStart,
            label = "krate-update",
            modifier = Modifier.fillMaxWidth(),
        ) { shown -> KrateUpdateStatusLine(state = shown) }
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
private fun KrateUpdateStatusLine(state: KrateUpdateUiState) {
    val colors = MaterialTheme.colorScheme
    when (state) {
        is KrateUpdateUiState.Idle ->
            KrateUpdateStatusRow(
                icon = painterResource(R.drawable.ic_system_update_alt_rounded),
                badgeColor = colors.surfaceContainerHighest,
                contentColor = colors.onSurfaceVariant,
                text = "Check for a newer version of Krate.",
            )

        is KrateUpdateUiState.Checking ->
            KrateUpdateStatusRow(
                icon = painterResource(R.drawable.ic_system_update_alt_rounded),
                badgeColor = colors.surfaceContainerHighest,
                contentColor = colors.onSurfaceVariant,
                text = rememberKrateLine(Moment.CHECKING_FOR_UPDATES),
            )

        is KrateUpdateUiState.Result -> KrateUpdateResultLine(status = state.status)
    }
}

@Composable
private fun KrateUpdateResultLine(status: KrateUpdateStatus) {
    val colors = MaterialTheme.colorScheme
    when (status) {
        is KrateUpdateStatus.UpToDate ->
            KrateUpdateStatusRow(
                icon = painterResource(R.drawable.ic_check_circle_rounded),
                badgeColor = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                text = rememberKrateLeadIn(Moment.KRATE_LATEST, "You're on the latest version."),
            )

        is KrateUpdateStatus.UpdateAvailable ->
            KrateUpdateStatusRow(
                icon = painterResource(R.drawable.ic_system_update_alt_rounded),
                badgeColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
                text = "Version ${status.latestVersion} is available.",
                emphasize = true,
            )

        is KrateUpdateStatus.NoReleasePublished ->
            KrateUpdateStatusRow(
                icon = painterResource(R.drawable.ic_info_rounded),
                badgeColor = colors.surfaceContainerHighest,
                contentColor = colors.onSurfaceVariant,
                text = "No Krate releases have been published yet.",
            )

        is KrateUpdateStatus.Failed ->
            KrateUpdateStatusRow(
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
    state: KrateUpdateUiState,
    button: DebouncedButtonState,
) {
    val checking = state is KrateUpdateUiState.Checking
    val label =
        when {
            state is KrateUpdateUiState.Idle -> "Check for updates"
            (state as? KrateUpdateUiState.Result)?.status is KrateUpdateStatus.Failed -> "Retry"
            else -> "Check again"
        }
    FilledTonalButton(
        onClick = { if (!checking && button.enabled) button.onClick() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        BusyButtonContent(busy = checking, text = label)
    }
}

// internal, not private - also called from KrateSelfUpdateContent.kt (same package)
@Composable
internal fun KrateUpdateStatusRow(
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
