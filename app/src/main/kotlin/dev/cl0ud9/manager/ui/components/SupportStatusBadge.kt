package dev.cl0ud9.manager.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.domain.model.SupportStatus

@Composable
fun SupportStatusBadge(
    status: SupportStatus,
    modifier: Modifier = Modifier,
) {
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
        shape = RoundedCornerShape(FULLY_ROUNDED_PERCENT),
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

private const val FULLY_ROUNDED_PERCENT = 50
