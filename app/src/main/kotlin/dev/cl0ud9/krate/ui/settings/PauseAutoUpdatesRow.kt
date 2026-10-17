package dev.cl0ud9.krate.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.platform.autoupdate.PauseLength
import dev.cl0ud9.krate.platform.autoupdate.isPausedAt
import dev.cl0ud9.krate.platform.autoupdate.pauseEndText
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
                icon = painterResource(R.drawable.ic_update_rounded),
                title = if (paused) "Automatic updates are paused" else "Pause automatic updates",
                subtitle =
                    when {
                        paused -> "Until ${pauseEndText(pausedUntil, now)}. New versions wait in Updates meanwhile."
                        available -> "Hold them back for a day or a week, say while you're travelling."
                        else -> "Turn on Automatic downloads and Install updates automatically to use this."
                    },
                colors = SettingsTint.BLUE.colors(),
            ),
        shape = shape,
        trailing = {
            Box {
                FilledTonalButton(
                    onClick = { if (paused) onResume() else menu = true },
                    enabled = paused || available,
                ) {
                    Text(if (paused) "Resume" else "Pause")
                }
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
    )
}
