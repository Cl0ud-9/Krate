package dev.cl0ud9.krate.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.platform.autoupdate.PauseLength
import dev.cl0ud9.krate.platform.autoupdate.isPausedAt
import dev.cl0ud9.krate.platform.autoupdate.pauseEndText
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.rememberClockUntil

// holds automatic updates back for a day or a week: one row under the switch, so no per-app controls are needed. While
// paused it says until when and offers Resume; the pause ends by itself, and the row notices when it does
@Composable
internal fun PauseAutoUpdatesRow(
    pausedUntil: Long,
    available: Boolean,
    onPause: (PauseLength) -> Unit,
    onResume: () -> Unit,
    shape: Shape,
) {
    val now = rememberClockUntil(pausedUntil)
    val paused = isPausedAt(pausedUntil, now)
    var menu by rememberSaveable { mutableStateOf(false) }
    SettingsRow(
        header =
            SettingsRowHeader(
                icon =
                    if (paused) {
                        rememberVectorPainter(
                            Icons.Filled.PauseCircle,
                        )
                    } else {
                        painterResource(R.drawable.ic_update_rounded)
                    },
                title = if (paused) "Automatic updates are paused" else "Pause automatic updates",
                subtitle =
                    when {
                        paused -> "New versions wait in Updates until it ends, or until you resume."
                        available -> "Hold them back for a day or a week, say while you're travelling."
                        else -> "Turn on Automatic downloads and Install updates automatically to use this."
                    },
                colors = (if (paused) SettingsTint.AMBER else SettingsTint.BLUE).colors(),
            ),
        shape = shape,
        trailing = {
            Box {
                PauseButton(paused = paused, enabled = paused || available) { if (paused) onResume() else menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    PauseLength.entries.forEach { length ->
                        DropdownMenuItem(
                            text = { Text(length.label) },
                            onClick = {
                                menu = false
                                onPause(length)
                            },
                        )
                    }
                }
            }
        },
        // while it lasts, when it ends: dropping in under the row as the pause starts, folding away as it ends
        expandedContent =
            if (paused) {
                { PauseEndNote(pauseEndText(pausedUntil, now)) }
            } else {
                null
            },
    )
}

// Pause is a quiet button; once paused the same button becomes the filled Resume, its colour and word blending over
@Composable
private fun PauseButton(
    paused: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (paused) colors.primary else colors.secondaryContainer,
        tween(BUTTON_BLEND_MS),
        label = "pause-container",
    )
    val content by animateColorAsState(
        if (paused) colors.onPrimary else colors.onSecondaryContainer,
        tween(BUTTON_BLEND_MS),
        label = "pause-content",
    )
    // as wide for Pause as for Resume, so the swap never changes the width the row's text wraps in
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.defaultMinSize(minWidth = BUTTON_MIN_WIDTH),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        AnimatedContent(
            targetState = paused,
            transitionSpec = { fadeIn(tween(BUTTON_BLEND_MS)) togetherWith fadeOut(tween(BUTTON_BLEND_MS)) },
            label = "pause-label",
        ) { shown -> Text(if (shown) "Resume" else "Pause") }
    }
}

@Composable
private fun PauseEndNote(end: String) {
    Surface(shape = ShapeCache.rounded12, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Back on $end",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

private const val BUTTON_BLEND_MS = 260
private val BUTTON_MIN_WIDTH = 112.dp
