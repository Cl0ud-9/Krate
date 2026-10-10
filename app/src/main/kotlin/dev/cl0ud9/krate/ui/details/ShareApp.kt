package dev.cl0ud9.krate.ui.details

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.platform.appContainer
import dev.cl0ud9.krate.ui.components.HeaderButtonColors
import dev.cl0ud9.krate.ui.components.HeaderIconButton
import dev.cl0ud9.krate.ui.navigation.BackButtonSize
import dev.cl0ud9.krate.ui.navigation.BackButtonTopPadding
import dev.cl0ud9.krate.ui.util.KRATE_REPO_URL

// the top-right Share on an app's page, for an app with a public home to point a friend at; Krate's own invite-only
// builds have none, so they don't get one
@Composable
fun BoxScope.ShareAppAction(appId: String) {
    val context = LocalContext.current
    val flow = remember(appId) { context.appContainer().catalogRepository.observeApp(appId) }
    val app by flow.collectAsStateWithLifecycle(initialValue = null)
    val shared = app ?: return
    val link = publicLinkOf(shared) ?: return
    HeaderIconButton(
        onClick = { context.shareApp(shared, link) },
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp, top = BackButtonTopPadding)
                .size(BackButtonSize),
        colors =
            HeaderButtonColors(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        Icon(Icons.Rounded.Share, contentDescription = "Share ${shared.displayName}")
    }
}

// where the app lives in public: its repository, wherever that is
private fun publicLinkOf(app: AppProfile): String? =
    (sourceOf(app) as? PackedFrom.Repo)?.let { "https://${it.forge.host}/${it.slug}" }

// a friend gets the app's name, what it's for, where to get it, and where to get Krate. One who already has Krate
// can share the message to it, and Krate opens the app's page
internal fun shareText(
    app: AppProfile,
    link: String,
): String {
    val about = app.description?.trim()?.let { if (it.last() in ".!?") it else "$it." }
    return listOfNotNull(
        "Thought you'd like ${app.displayName}." + (about?.let { " $it" } ?: ""),
        "Get it here: $link",
        "I keep it up to date with Krate: $KRATE_REPO_URL",
    ).joinToString("\n\n")
}

private fun Context.shareApp(
    app: AppProfile,
    link: String,
) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, app.displayName)
            putExtra(Intent.EXTRA_TEXT, shareText(app, link))
        }
    startActivity(Intent.createChooser(send, "Share ${app.displayName}"))
}
