package dev.cl0ud9.manager.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

private const val THUMB_ICON_FADE_MS = 120

// every Switch in the app goes through here instead of a bare Material Switch - a checkmark/close
// glyph crossfading inside the thumb plus a toggle-specific haptic tick on every flip, giving
// settings toggles more weight than the plain default thumb
@Composable
fun ManagerSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    Switch(
        checked = checked,
        onCheckedChange = {
            haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onCheckedChange(it)
        },
        modifier = modifier,
        enabled = enabled,
        thumbContent = {
            AnimatedContent(
                targetState = checked,
                label = "switch-thumb-icon",
                transitionSpec = {
                    fadeIn(tween(THUMB_ICON_FADE_MS)) togetherWith fadeOut(tween(THUMB_ICON_FADE_MS))
                },
            ) { isChecked ->
                Icon(
                    imageVector = if (isChecked) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        },
    )
}
