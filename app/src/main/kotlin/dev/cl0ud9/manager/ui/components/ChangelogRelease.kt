package dev.cl0ud9.manager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.platform.selfupdate.ManagerRelease
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.util.formatMarkdownLite
import java.text.DateFormat
import java.time.Instant
import java.util.Date

@Composable
internal fun ReleaseItem(release: ManagerRelease) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VersionBadge(version = release.version)
            Spacer(modifier = Modifier.weight(1f))
            releaseDate(release.publishedAt)?.let { date ->
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ReleaseNotesCard(notes = releaseNoteItems(release.notes))
    }
}

@Composable
private fun VersionBadge(version: String) {
    Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape) {
        Text(
            text = version,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun ReleaseNotesCard(notes: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = ShapeCache.smooth24,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                text = "What's new",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            notes.ifEmpty { listOf("No notes for this version.") }.forEachIndexed { index, note ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
                NoteRow(note = note, first = index == 0)
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: String,
    first: Boolean,
) {
    Row(
        modifier = Modifier.padding(top = if (first) 12.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 8.dp)
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Text(
            text = note.formatMarkdownLite(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// a release body's "- " bullets become one row each; any loose paragraph is a row of its own
internal fun releaseNoteItems(notes: String): List<String> {
    val items = mutableListOf<String>()
    notes.lines().map { it.trim() }.forEach { line ->
        when {
            line.isEmpty() -> Unit
            line.startsWith("- ") || line.startsWith("* ") -> items += line.drop(2).trim()
            items.isNotEmpty() && !line.startsWith("#") -> items[items.lastIndex] = items.last() + " " + line
            else -> items += line.removePrefix("#").trim()
        }
    }
    return items
}

private fun releaseDate(publishedAt: String?): String? =
    publishedAt?.let {
        runCatching { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(Instant.parse(it).toEpochMilli())) }
            .getOrNull()
    }
