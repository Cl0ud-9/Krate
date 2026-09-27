package dev.cl0ud9.manager.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.domain.model.ThemeMode
import dev.cl0ud9.manager.ui.theme.ShapeCache

private class ThemeOption(
    val mode: ThemeMode,
    @param:DrawableRes val icon: Int,
    val title: String,
    val description: String,
    val recommended: Boolean = false,
)

private val THEME_OPTIONS =
    listOf(
        ThemeOption(
            ThemeMode.DARK,
            R.drawable.ic_dark_mode_rounded,
            "Dark",
            "Krate's default look, easy on the eyes.",
            recommended = true,
        ),
        ThemeOption(
            ThemeMode.LIGHT,
            R.drawable.ic_light_mode_rounded,
            "Light",
            "A brighter look across the whole app.",
        ),
        ThemeOption(
            ThemeMode.SYSTEM,
            R.drawable.ic_smartphone_rounded,
            "Follow system",
            "Match your phone's own light or dark setting.",
        ),
    )

// the three theme choices as big tappable cards, used by setup and by Settings > Appearance
@Composable
internal fun ThemeModeCards(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        THEME_OPTIONS.forEach { option ->
            ThemeModeCard(option = option, selected = option.mode == selected, onClick = { onSelect(option.mode) })
        }
    }
}

@Composable
private fun ThemeModeCard(
    option: ThemeOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        label = "theme",
    )
    val content by animateColorAsState(
        if (selected) colors.onPrimaryContainer else colors.onSurface,
        label = "themeContent",
    )
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.smooth28,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = ShapeCache.smooth20,
                color = if (selected) content.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    painter = painterResource(option.icon),
                    contentDescription = null,
                    tint = if (selected) content else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(14.dp).size(26.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = option.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (option.recommended) RecommendedBadge()
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.8f),
                )
            }
            SelectionMark(selected = selected, color = content)
        }
    }
}

@Composable
private fun RecommendedBadge() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
        Text(
            text = "Recommended",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
internal fun SelectionMark(
    selected: Boolean,
    color: Color,
) {
    if (selected) {
        Icon(
            painterResource(R.drawable.ic_check_circle_rounded),
            contentDescription = "Selected",
            tint = color,
            modifier = Modifier.size(28.dp),
        )
    } else {
        Box(modifier = Modifier.size(28.dp).padding(3.dp).border(2.dp, color.copy(alpha = 0.4f), CircleShape))
    }
}
