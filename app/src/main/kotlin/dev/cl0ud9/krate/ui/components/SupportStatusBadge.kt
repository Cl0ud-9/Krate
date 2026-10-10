package dev.cl0ud9.krate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.SupportStatus
import dev.cl0ud9.krate.ui.navigation.glassRim

private const val TRACKED_TINT = 0.16f

@Composable
fun SupportStatusBadge(
    status: SupportStatus,
    modifier: Modifier = Modifier,
) {
    // supported is what every app in the Krate is expected to be, so only the exceptions get a badge
    if (status == SupportStatus.SUPPORTED) return
    val (label, containerColor, contentColor) =
        when (status) {
            SupportStatus.SUPPORTED -> {
                Triple(
                    "Supported",
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            SupportStatus.BETA -> {
                Triple(
                    "Beta",
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            SupportStatus.DEPRECATED -> {
                Triple(
                    "Deprecated",
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SupportStatus.TEMPORARILY_UNAVAILABLE -> {
                Triple(
                    "Unavailable",
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

    Surface(
        modifier = modifier,
        // pill shape from the shared token set (extraLarge), not a one-off RoundedCornerShape -
        // keeps this badge on the same shape scale as every other component
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

// an app the person follows from GitHub themselves, rather than one picked for the Krate: a soft tint of [tone] rather
// than a solid pill, so it sits well on any card, including one tinted by the app's own icon
@Composable
fun TrackedBadge(
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.colorScheme.primary,
    // GitHub's mark for an app from GitHub, a globe for one from another site
    onGitHub: Boolean = true,
) {
    Surface(
        modifier = modifier.glassRim(MaterialTheme.shapes.extraLarge),
        shape = MaterialTheme.shapes.extraLarge,
        color = tone.copy(alpha = TRACKED_TINT),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painterResource(if (onGitHub) R.drawable.ic_github else R.drawable.ic_public_rounded),
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(12.dp),
            )
            Text(text = "Tracked by you", style = MaterialTheme.typography.labelMedium)
        }
    }
}
