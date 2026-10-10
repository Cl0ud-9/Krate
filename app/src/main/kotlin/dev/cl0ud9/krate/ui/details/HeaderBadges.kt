package dev.cl0ud9.krate.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.fromGitHub
import dev.cl0ud9.krate.domain.model.isTracked
import dev.cl0ud9.krate.domain.model.latestArtifact
import dev.cl0ud9.krate.ui.components.SupportStatusBadge
import dev.cl0ud9.krate.ui.components.TrackedBadge

// tracked or not, beta or not, and the newest version, on one line under the name
@Composable
internal fun HeaderBadges(
    app: AppProfile,
    accent: Color?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (app.isTracked) TrackedBadge(tone = accent ?: MaterialTheme.colorScheme.primary, onGitHub = app.fromGitHub)
        SupportStatusBadge(status = app.supportStatus)
        Text(
            text = app.latestArtifact?.versionName?.let { "Latest $it" } ?: "Latest version unknown",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
