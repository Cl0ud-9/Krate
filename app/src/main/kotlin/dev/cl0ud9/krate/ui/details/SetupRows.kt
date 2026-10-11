package dev.cl0ud9.krate.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.AppGuide
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.WalkStep
import dev.cl0ud9.krate.ui.util.tappableRow

// the settings worth knowing and how to keep them, each a row that opens its own sheet
@Composable
internal fun MoreRows(
    app: AppProfile,
    guide: AppGuide,
    divided: Boolean,
    onRecommended: () -> Unit,
) {
    if (!guide.hasMoreRows && app.openLinks == null) return
    if (divided) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ROW_DIVIDER_ALPHA))
    Column {
        if (guide.walkthrough.isNotEmpty()) WalkthroughRow(app, guide.walkthrough)
        guide.recommended?.let { picks ->
            SheetRow(
                icon = rememberVectorPainter(Icons.Filled.AutoAwesome),
                title = "Recommended settings",
                subtitle = recommendedSubtitle(app, picks),
                onClick = onRecommended,
            )
        }
        if (app.openLinks != null) OpenLinksRow(app)
        if (guide.tips.isNotEmpty()) TipsRow(app, guide)
        if (guide.backup != null) KeepRow(app, guide)
    }
}

@Composable
private fun WalkthroughRow(
    app: AppProfile,
    steps: List<WalkStep>,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SheetRow(
        icon = rememberVectorPainter(Icons.Filled.Checklist),
        title = "Step-by-step setup",
        subtitle = walkthroughSubtitle(app, steps),
        onClick = { open = true },
    )
    if (open) WalkthroughSheet(app = app, steps = steps, onDismiss = { open = false })
}

@Composable
private fun TipsRow(
    app: AppProfile,
    guide: AppGuide,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SheetRow(
        icon = rememberVectorPainter(Icons.Filled.Lightbulb),
        title = "Settings worth a look",
        subtitle = guide.settingsWhere?.let { "In $it" } ?: "${guide.tips.size} tips",
        onClick = { open = true },
    )
    if (open) TipsSheet(app = app, guide = guide, onDismiss = { open = false })
}

@Composable
private fun KeepRow(
    app: AppProfile,
    guide: AppGuide,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SheetRow(
        icon = rememberVectorPainter(Icons.Filled.SettingsBackupRestore),
        title = "Keep your settings",
        subtitle = keepSubtitle(app, guide),
        onClick = { open = true },
    )
    if (open) KeepSettingsSheet(app = app, guide = guide, onDismiss = { open = false })
}

@Composable
internal fun SheetRow(
    icon: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    highlight: Boolean = false,
) {
    val accent = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().tappableRow(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(BADGE),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = accent,
            )
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right_rounded),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// whether the guide has anything for the rows under the steps
private val AppGuide.hasMoreRows: Boolean
    get() = tips.isNotEmpty() || backup != null || recommended != null || walkthrough.isNotEmpty()

private const val ROW_DIVIDER_ALPHA = 0.5f
