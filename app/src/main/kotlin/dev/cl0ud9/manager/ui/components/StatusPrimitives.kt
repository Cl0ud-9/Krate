package dev.cl0ud9.manager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

// shared across App Details' download/install sections and Settings - a generic icon+tint+text
// row and a small helper caption, not specific to any one screen

// every in-progress state across those screens shares this exact indicator - one definition
// instead of several copies. determinate progress (real download bytes) keeps the wavy linear
// bar, since a filled fraction is genuinely informative there. indeterminate states (installing,
// uninstalling, waiting for the user) used to reuse the same indeterminate wavy bar, but that
// animates as two independently-phased wavy segments chasing each other - readable as an actual
// progress bar when it's genuinely determinate, but noisy and easy to misread as "two bars" when
// there is no real progress fraction behind it. the expressive LoadingIndicator (a single
// morphing shape) is Material's own component for exactly this indeterminate case, so it
// replaces the wavy bar rather than reusing it just because it's already wired up
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ManagerLinearProgress(progress: Float?) {
    if (progress != null) {
        // stock M3 ramps the wave amplitude down to 0 below 10% and above 95% progress (settling
        // down as it starts/finishes) - held constant here instead, since a download nearing
        // completion flattening out read as the progress bar stalling rather than almost done
        LinearWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            amplitude = { 1f },
        )
    } else {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
    }
}

@Composable
internal fun StatusRow(
    icon: Painter,
    tint: Color,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun HelperText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
