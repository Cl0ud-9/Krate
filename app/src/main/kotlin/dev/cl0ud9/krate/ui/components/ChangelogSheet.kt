package dev.cl0ud9.krate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.data.downloads.friendlyNetworkError
import dev.cl0ud9.krate.platform.appContainer
import dev.cl0ud9.krate.platform.selfupdate.KrateRelease
import dev.cl0ud9.krate.ui.theme.ShapeCache
import java.io.IOException

private const val MAX_RELEASES = 6
private const val RELEASES_URL = "https://github.com/Cl0ud-9/Krate/releases"

// What's new = Krate's real release notes from GitHub, newest first - a list written into the
// app itself goes stale the moment a release forgets to update it
@Composable
fun HomeChangelogAction() {
    var showChangelog by rememberSaveable { mutableStateOf(false) }
    FilledIconButton(
        onClick = { showChangelog = true },
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        Icon(painterResource(R.drawable.ic_newspaper_rounded), contentDescription = "What's new")
    }
    if (showChangelog) {
        ChangelogSheet(onDismiss = { showChangelog = false })
    }
}

private sealed interface ReleasesState {
    data object Loading : ReleasesState

    data class Loaded(
        val releases: List<KrateRelease>,
    ) : ReleasesState

    data class Failed(
        val reason: String,
    ) : ReleasesState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState<ReleasesState>(ReleasesState.Loading, attempt) {
        value = ReleasesState.Loading
        value =
            try {
                ReleasesState.Loaded(
                    context
                        .appContainer()
                        .krateUpdateChecker
                        .recentReleases()
                        .take(MAX_RELEASES),
                )
            } catch (exception: IOException) {
                ReleasesState.Failed(friendlyNetworkError(exception))
            }
    }
    // peeks at half height first, then drags up to full
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // full height from the start, so the sheet has a half-height peek to open at even while the notes load
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            Column(
                // the notes fade out above the screen's bottom edge and the system bar, even at the half-height peek
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fadeAboveSystemBar(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ChangelogHeader()
                ReleasesContent(state = state, onRetry = { attempt++ })
            }
            ViewOnGitHubButton(modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp))
        }
    }
}

// the sheet's title over the animated wave divider
@Composable
private fun ChangelogHeader() {
    Text(
        text = "What's new",
        style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 44.sp),
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(16.dp))
    SineWaveLine(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(horizontal = 8.dp)
                .padding(bottom = 4.dp),
        animate = true,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
        alpha = 0.95f,
        strokeWidth = 4.dp,
        amplitude = 4.dp,
        waves = 7.6f,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ViewOnGitHubButton(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    MediumExtendedFloatingActionButton(
        onClick = { uriHandler.openUri(RELEASES_URL) },
        shape = ShapeCache.rounded16,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        icon = { Icon(painterResource(R.drawable.ic_github), contentDescription = null) },
        text = { Text("View on GitHub") },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReleasesContent(
    state: ReleasesState,
    onRetry: () -> Unit,
) {
    when (state) {
        ReleasesState.Loading ->
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }

        is ReleasesState.Failed ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = state.reason,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = onRetry) { Text("Try again") }
            }

        is ReleasesState.Loaded ->
            LazyColumn(
                // no custom scroll handling: the list hands its leftover drag and fling to the sheet, so pulling down
                // from the top of the list drags the sheet and a flick down closes it
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                items(state.releases, key = { it.version }) { release -> ReleaseItem(release) }
            }
    }
}
