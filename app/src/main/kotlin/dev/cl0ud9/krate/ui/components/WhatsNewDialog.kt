package dev.cl0ud9.krate.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.platform.selfupdate.KrateRelease
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.voice.Moment
import dev.cl0ud9.krate.voice.rememberKrateLine

// shown once, on the first open after Krate updated itself
@Composable
fun WhatsNewDialog(
    release: KrateRelease,
    onDismiss: () -> Unit,
) {
    KrateDialog(
        header =
            KrateDialogHeader(
                tag = "Updated to ${release.version}",
                icon = painterResource(R.drawable.ic_krate),
                title = rememberKrateLine(Moment.KRATE_UPDATED),
                body = "Krate is on version ${release.version} now. Here's what changed.",
            ),
        onDismissRequest = onDismiss,
        detail = { ReleaseNotesHint(title = "What's new", notes = releaseNoteItems(release.notes)) },
        footer = {
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onDismiss, shape = ShapeCache.rounded16) {
                Icon(
                    painterResource(R.drawable.ic_arrow_forward_rounded),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Got it")
            }
        },
    )
}
