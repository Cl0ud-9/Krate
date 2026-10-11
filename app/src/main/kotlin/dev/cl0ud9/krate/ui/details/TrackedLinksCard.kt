package dev.cl0ud9.krate.ui.details

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.theme.ShapeCache

// an app someone tracks themselves has no setup card, so its one row about links gets a card of its own, shown only
// when the app declares addresses nobody else has verified
@Composable
internal fun TrackedLinksCard(app: AppProfile) {
    val standing = rememberLinkStanding(app) ?: return
    if (standing.wanted.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth().glassRim(ShapeCache.rounded16),
        shape = ShapeCache.rounded16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        androidx.compose.foundation.layout
            .Box(modifier = Modifier.padding(16.dp)) { OpenLinksRow(app) }
    }
}
