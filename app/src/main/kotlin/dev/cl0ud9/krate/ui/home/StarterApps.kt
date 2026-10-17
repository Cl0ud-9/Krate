package dev.cl0ud9.krate.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.ui.components.AppIconAvatar
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.rememberDebouncedOnClick
import dev.cl0ud9.krate.ui.util.rememberLastNonNull
import dev.cl0ud9.krate.ui.util.tappableRow

// a few good first apps for a phone with nothing from the shelf on it yet, each with the line on why; it folds away on
// its own as soon as one of them is installed, and leaves no gap when there's nothing to show
@Composable
internal fun StarterApps(
    apps: List<AppProfile>,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // kept while the card folds away, so it doesn't go blank halfway
    val shown = rememberLastNonNull(apps.takeIf { it.isNotEmpty() }).orEmpty()
    AnimatedVisibility(
        visible = apps.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Start with these", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Card(
                modifier = Modifier.fillMaxWidth().glassRim(ShapeCache.rounded16),
                shape = ShapeCache.rounded16,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                shown.forEachIndexed { index, app ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA),
                        )
                    }
                    StarterRow(app = app, onClick = { onOpenApp(app.id) })
                }
            }
        }
    }
}

private const val DIVIDER_ALPHA = 0.5f

@Composable
private fun StarterRow(
    app: AppProfile,
    onClick: () -> Unit,
) {
    val debounced = rememberDebouncedOnClick(onClick = onClick)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).tappableRow(onClick = debounced),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconAvatar(app = app, size = 44.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = app.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = app.pitch.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right_rounded),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}
