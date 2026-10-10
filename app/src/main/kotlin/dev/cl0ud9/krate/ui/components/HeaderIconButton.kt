package dev.cl0ud9.krate.ui.components

import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// a small round button on a header (What's new, Settings, Back). Solid in both looks: with liquid glass on it sits on
// the glass header, and glass on glass only muddies it
@Composable
fun HeaderIconButton(
    onClick: () -> Unit,
    colors: HeaderButtonColors,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = colors.solid,
                contentColor = colors.content,
            ),
        content = icon,
    )
}
