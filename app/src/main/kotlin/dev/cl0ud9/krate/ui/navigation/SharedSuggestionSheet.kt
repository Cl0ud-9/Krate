package dev.cl0ud9.krate.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.cl0ud9.krate.platform.appContainer
import dev.cl0ud9.krate.ui.apps.SharedSuggestion
import dev.cl0ud9.krate.ui.apps.SharedSuggestions
import dev.cl0ud9.krate.ui.apps.SuggestAppSheet
import dev.cl0ud9.krate.ui.apps.TrackAppSheet
import dev.cl0ud9.krate.ui.apps.publishedFrom
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

// a link shared to "Suggest to Krate": an app Krate already carries (or one you track) opens on its own page, anything
// else opens Suggest an app with the link filled in, which can also hand it to Track an app
@Composable
internal fun SharedSuggestionSheet(navController: NavHostController) {
    val pending by SharedSuggestions.pending.collectAsStateWithLifecycle()
    val catalog = LocalContext.current.appContainer().catalogRepository
    var showing by remember { mutableStateOf<SharedSuggestion?>(null) }
    var tracking by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pending) {
        val suggestion = pending ?: return@LaunchedEffect
        SharedSuggestions.clear()
        val existing =
            suggestion.githubRepo?.let { repo ->
                withTimeoutOrNull(CATALOG_WAIT_MS) { catalog.observeApps().first() }?.publishedFrom(repo)
            }
        if (existing != null) navController.navigateFromOutside("apps/${existing.id}") else showing = suggestion
    }
    showing?.let {
        SuggestAppSheet(
            onDismiss = { showing = null },
            initial = it,
            onTrackInstead = { link ->
                showing = null
                tracking = link
            },
        )
    }
    tracking?.let { link ->
        TrackAppSheet(
            onDismiss = { tracking = null },
            onOpenApp = { id -> navController.navigateFromTap("apps/$id") },
            initialLink = link,
        )
    }
}

private const val CATALOG_WAIT_MS = 3000L
