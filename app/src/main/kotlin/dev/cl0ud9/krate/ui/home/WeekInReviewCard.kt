package dev.cl0ud9.krate.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.ui.components.FoldAway
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.voice.KrateVoice
import dev.cl0ud9.krate.voice.KrateWeek
import dev.cl0ud9.krate.voice.Moment
import dev.cl0ud9.krate.voice.rememberKrateLine
import dev.cl0ud9.krate.voice.weekInReviewText
import java.time.ZonedDateTime

// a look back at last week, from the first open of a new week until it's put away; only with playful messages on,
// and only when last week had something in it
@Composable
internal fun WeekInReviewCard(
    modifier: Modifier = Modifier,
    gap: Dp = 0.dp,
) {
    val now = remember { ZonedDateTime.now() }
    val text = remember { weekInReviewText(KrateWeek.lastWeek(now)) }
    var shown by remember { mutableStateOf(!KrateWeek.dismissed(now)) }
    FoldAway(
        visible = shown && text != null && KrateVoice.playful,
        modifier = modifier,
        gap = gap,
        animateIn = false,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().glassRim(ShapeCache.rounded16),
            shape = ShapeCache.rounded16,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 6.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(shape = ShapeCache.rounded12, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Icon(
                        painterResource(R.drawable.ic_newspaper_rounded),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = rememberKrateLine(Moment.WEEKLY),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = text.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        shown = false
                        KrateWeek.dismiss(now)
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
