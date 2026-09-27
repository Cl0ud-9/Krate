package dev.cl0ud9.krate.ui.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.githubNewIssueUrl
import dev.cl0ud9.krate.ui.util.ownsScroll
import dev.cl0ud9.krate.ui.util.steadyHeight

private const val WHY_MAX_LINES = 5

// the end of the Apps list: what kind of apps belong in Krate, and a way to ask for one
@Composable
fun SuggestAppCard(modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeCache.smooth24,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = ShapeCache.smooth16, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Icon(
                        painterResource(R.drawable.ic_campaign_rounded),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(10.dp).size(22.dp),
                    )
                }
                Text(
                    text = "Know an app that belongs here?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text =
                    "Krate is for apps you won't find on the Play Store, " +
                        "mostly open-source apps published on GitHub. " +
                        "Suggest one and it might land in the Krate.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = { open = true }) {
                Icon(
                    painterResource(R.drawable.ic_arrow_forward_rounded),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Suggest an app")
            }
        }
    }
    if (open) SuggestAppSheet(onDismiss = { open = false })
}

// name, where to find it, and why - sent from any app, or as a GitHub issue
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestAppSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // name, then link, then why - the keyboard's Next key walks through them in order
    val name = rememberTextFieldState()
    val link = rememberTextFieldState()
    val why = rememberTextFieldState()
    val whyScroll = rememberScrollState()
    val suggestion = Suggestion(name.text.trim().toString(), link.text.trim().toString(), why.text.trim().toString())
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SuggestHeading()
            val next = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
            OutlinedTextField(
                state = name,
                label = { Text("App name") },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = next,
                modifier = Modifier.fillMaxWidth().steadyHeight(),
            )
            OutlinedTextField(
                state = link,
                label = { Text("Link (GitHub or website)") },
                lineLimits = TextFieldLineLimits.SingleLine,
                // only flagged once something is typed, so an untouched field isn't shown as an error
                isError = link.text.isNotBlank() && !looksLikeLink(link.text.trim().toString()),
                supportingText = { Text("Like github.com/owner/app") },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
                modifier = Modifier.fillMaxWidth().steadyHeight(),
            )
            OutlinedTextField(
                state = why,
                label = { Text("What's it for? (optional)") },
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = WHY_MAX_LINES),
                scrollState = whyScroll,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().ownsScroll(whyScroll),
            )
            SuggestActions(
                suggestion = suggestion,
                onShare = { context.startActivity(suggestion.shareIntent()) },
                onGitHub = { suggestion.openIssue(context) },
            )
        }
    }
}

@Composable
private fun SuggestHeading() {
    Text(text = "Suggest an app", style = MaterialTheme.typography.headlineSmall)
    Text(
        text = "Apps that live outside the Play Store fit best, like open-source apps released on GitHub.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// send it from any app, or open it as a GitHub issue; both need a name and a link
@Composable
private fun SuggestActions(
    suggestion: Suggestion,
    onShare: () -> Unit,
    onGitHub: () -> Unit,
) {
    Button(onClick = onShare, enabled = suggestion.isComplete, modifier = Modifier.fillMaxWidth()) {
        Text("Send suggestion")
    }
    FilledTonalButton(onClick = onGitHub, enabled = suggestion.isComplete, modifier = Modifier.fillMaxWidth()) {
        Icon(painterResource(R.drawable.ic_github), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Suggest on GitHub instead")
    }
}

private class Suggestion(
    val name: String,
    val link: String,
    val why: String,
) {
    // a name and a link are what's needed to look an app up; the reason is a bonus
    val isComplete: Boolean
        get() = name.isNotEmpty() && looksLikeLink(link)

    private val body: String
        get() =
            buildString {
                append("App: ").append(name)
                if (link.isNotEmpty()) append("\nLink: ").append(link)
                if (why.isNotEmpty()) append("\n\n").append(why)
            }

    fun shareIntent(): Intent =
        Intent(Intent.ACTION_SEND)
            .apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "App suggestion for Krate: $name")
                putExtra(Intent.EXTRA_TEXT, body)
            }.let { Intent.createChooser(it, "Send suggestion with") }

    fun openIssue(context: Context) {
        val url = githubNewIssueUrl(title = "App suggestion: $name", body = body)
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}

// a web address with or without https://, like github.com/owner/app - no spaces, and a dot in the site name
internal fun looksLikeLink(text: String): Boolean {
    val site = text.removePrefix("https://").removePrefix("http://").substringBefore('/')
    return text.isNotEmpty() &&
        text.none { it.isWhitespace() } &&
        '.' in site &&
        !site.startsWith('.') &&
        !site.endsWith('.')
}
