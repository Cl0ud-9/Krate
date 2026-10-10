package dev.cl0ud9.krate.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.trackedRef
import dev.cl0ud9.krate.domain.tracked.Forge
import dev.cl0ud9.krate.ui.apps.SharedSuggestion
import dev.cl0ud9.krate.ui.apps.SuggestAppSheet
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.voice.Moment
import dev.cl0ud9.krate.voice.rememberKrateLine

// on an app the person tracks themselves: if they like it, a suggestion for the Krate comes filled in from what Krate
// already knows about it, in the same look as the Apps tab's own suggest card
@Composable
internal fun ShareFindCard(app: AppProfile) {
    var suggesting by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().glassRim(ShapeCache.rounded16),
        shape = ShapeCache.rounded16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = ShapeCache.rounded12, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Icon(
                        painterResource(R.drawable.ic_campaign_rounded),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                    )
                }
                Text(
                    text = rememberKrateLine(Moment.TRACK_SHARE),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text =
                    "Suggest ${app.displayName} and it could join the Krate, checked and kept up to date for " +
                        "everyone, one tap away.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = { suggesting = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painterResource(R.drawable.ic_arrow_forward_rounded),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Suggest it")
            }
        }
    }
    if (suggesting) {
        val ref = app.trackedRef
        SuggestAppSheet(
            onDismiss = { suggesting = false },
            initial =
                SharedSuggestion(
                    name = app.displayName,
                    link = ref?.webUrl?.removePrefix("https://").orEmpty(),
                    why = app.description.orEmpty(),
                    githubRepo = ref?.takeIf { it.forge == Forge.GITHUB }?.slug,
                ),
        )
    }
}
