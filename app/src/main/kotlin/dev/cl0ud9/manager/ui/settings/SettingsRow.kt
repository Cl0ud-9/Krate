package dev.cl0ud9.manager.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.ui.theme.ShapeCache

// bundles the badge's container/content color pair - keeps SettingsRow under detekt's
// parameter-count threshold without collapsing them into a single ambiguous "tint" value
internal data class SettingsRowColors(
    val badgeColor: Color,
    val contentColor: Color,
)

@Composable
internal fun defaultSettingsRowColors() =
    SettingsRowColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)

// the icon/title/subtitle/colors that make up a row's header, bundled together so SettingsRow
// itself stays under detekt's parameter-count threshold
internal data class SettingsRowHeader(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val colors: SettingsRowColors,
)

// icon badge + title + subtitle as one list-row header, with an optional expanded body below a
// divider for rows that need more than a single trailing control (a button, a dynamic status) -
// every row is its own rounded card so the list reads as discrete, tappable-feeling blocks rather
// than one continuous settings sheet. a flowing list of these rows, instead of plain text blocks
// inside flat cards, makes the settings screen feel considered rather than default-Material-boilerplate
@Composable
internal fun SettingsRow(
    header: SettingsRowHeader,
    trailing: (@Composable () -> Unit)? = null,
    expandedContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(ShapeCache.smooth14).background(header.colors.badgeColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        header.icon,
                        contentDescription = null,
                        tint = header.colors.contentColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = header.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = header.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                trailing?.invoke()
            }
            if (expandedContent != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = expandedContent)
            }
        }
    }
}
