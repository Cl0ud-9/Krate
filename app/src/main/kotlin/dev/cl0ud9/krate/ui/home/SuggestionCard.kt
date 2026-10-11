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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.krate.domain.suggest.Suggestion
import dev.cl0ud9.krate.domain.suggest.SuggestionKind
import dev.cl0ud9.krate.platform.suggest.SuggestionStore
import dev.cl0ud9.krate.ui.components.AppIconAvatar
import dev.cl0ud9.krate.ui.components.ButtonRow
import dev.cl0ud9.krate.ui.navigation.SharedIconOrigin
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.navigation.sharedAppIcon
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.rememberLastNonNull
import dev.cl0ud9.krate.voice.SuggestionLines

internal const val SUGGESTION_PLACE = "suggestion"

// one app from the shelf, put forward with a line on what it does for someone. It folds in and out rather than
// popping, and leaves no gap when there's nothing to say
@Composable
internal fun SuggestionCard(
    suggestion: Suggestion?,
    nowMillis: Long,
    onShow: (Suggestion) -> Unit,
    onPass: (Suggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    // kept while the card folds away, so it doesn't go blank halfway
    val shown = rememberLastNonNull(suggestion)
    AnimatedVisibility(
        visible = suggestion != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        shown?.let { SuggestionBody(it, nowMillis, onShow, onPass) }
    }
}

@Composable
private fun SuggestionBody(
    suggestion: Suggestion,
    nowMillis: Long,
    onShow: (Suggestion) -> Unit,
    onPass: (Suggestion) -> Unit,
) {
    val app = suggestion.app
    Card(
        modifier = Modifier.fillMaxWidth().glassRim(ShapeCache.rounded16),
        shape = ShapeCache.rounded16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                AppIconAvatar(app = app, size = 48.dp, modifier = Modifier.sharedAppIcon(app.id, SUGGESTION_PLACE))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = SuggestionLines.kicker(suggestion.kind, app.id, nowMillis),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(text = app.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = app.pitch.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    suggestion.becauseOf?.let { owned ->
                        Text(
                            text = SuggestionLines.because(owned, app.id, nowMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            ButtonRow {
                TextButton(onClick = { onPass(suggestion) }) {
                    Text(if (suggestion.kind == SuggestionKind.NEW) "Got it" else "Not for me")
                }
                Button(onClick = { onShow(suggestion) }) { Text("Show me") }
            }
        }
    }
}

// the suggestion for now and what its two buttons do: Show me opens the app's page and puts it aside for a week; the
// other turns a new arrival into one seen, or an app that isn't for them into one never offered again
@Composable
internal fun HomeSuggestion(
    viewModel: HomeViewModel,
    onNavigateToApp: (String) -> Unit,
) {
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()
    SuggestionCard(
        suggestion = suggestion,
        nowMillis = System.currentTimeMillis(),
        onShow = { picked ->
            SuggestionStore.looked(picked.app.id, System.currentTimeMillis())
            SharedIconOrigin.tapped(picked.app.id, SUGGESTION_PLACE)
            onNavigateToApp(picked.app.id)
        },
        onPass = { picked ->
            if (picked.kind == SuggestionKind.NEW) {
                SuggestionStore.markSeen(picked.app.id)
            } else {
                SuggestionStore.turnDown(picked.app.id)
            }
        },
    )
}
