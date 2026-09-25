package dev.cl0ud9.manager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.domain.model.AnnouncementItem
import dev.cl0ud9.manager.domain.model.AnnouncementSeverity
import dev.cl0ud9.manager.ui.theme.ShapeCache

// a catalog announcement (see catalog/announcements.json) - tinted by severity, with an optional
// shortcut to the app it points at (a temporary alternative, say) and a close button when allowed
@Composable
fun AnnouncementCard(
    item: AnnouncementItem,
    onOpenApp: (String) -> Unit,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val announcement = item.announcement
    val (container, content) = severityColors(announcement.severity)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    painter = painterResource(severityIcon(announcement.severity)),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = announcement.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (announcement.dismissible) {
                    IconButton(onClick = { onDismiss(announcement.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text(text = announcement.message, style = MaterialTheme.typography.bodyMedium)
            val actionAppId = announcement.actionAppId
            if (actionAppId != null && item.actionAppName != null) {
                Button(
                    onClick = { onOpenApp(actionAppId) },
                    colors = ButtonDefaults.buttonColors(containerColor = content, contentColor = container),
                ) {
                    Text("Open ${item.actionAppName}")
                }
            }
        }
    }
}

@Composable
private fun severityColors(severity: AnnouncementSeverity): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (severity) {
        AnnouncementSeverity.INFO -> scheme.secondaryContainer to scheme.onSecondaryContainer
        AnnouncementSeverity.WARNING -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        AnnouncementSeverity.CRITICAL -> scheme.errorContainer to scheme.onErrorContainer
    }
}

private fun severityIcon(severity: AnnouncementSeverity): Int =
    when (severity) {
        AnnouncementSeverity.INFO -> R.drawable.ic_info_rounded
        AnnouncementSeverity.WARNING -> R.drawable.ic_campaign_rounded
        AnnouncementSeverity.CRITICAL -> R.drawable.ic_error_rounded
    }
