package dev.cl0ud9.manager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.data.downloads.friendlyNetworkError
import dev.cl0ud9.manager.platform.appContainer
import dev.cl0ud9.manager.platform.selfupdate.ManagerRelease
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.util.formatMarkdownLite
import java.io.IOException
import java.text.DateFormat
import java.time.Instant
import java.util.Date

private const val MAX_RELEASES = 6

// What's new = the manager's real release notes from GitHub, newest first - a list written into the
// app itself goes stale the moment a release forgets to update it
@Composable
fun HomeChangelogAction() {
    var showChangelog by remember { mutableStateOf(false) }
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
        val releases: List<ManagerRelease>,
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
                        .managerUpdateChecker
                        .recentReleases()
                        .take(MAX_RELEASES),
                )
            } catch (exception: IOException) {
                ReleasesState.Failed(friendlyNetworkError(exception))
            }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "What's new",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(16.dp))
            SineWaveLine(
                modifier = Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 8.dp),
                animate = true,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                alpha = 0.95f,
                strokeWidth = 4.dp,
                amplitude = 4.dp,
                waves = 7.6f,
            )
            ReleasesContent(state = state, onRetry = { attempt++ })
        }
    }
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
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                items(state.releases) { release -> ReleaseCard(release) }
            }
    }
}

@Composable
private fun ReleaseCard(release: ManagerRelease) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = ShapeCache.smooth20,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Version ${release.version}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            releaseDate(release.publishedAt)?.let { date ->
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = release.notes.ifBlank { "No notes for this version." }.formatMarkdownLite(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun releaseDate(publishedAt: String?): String? =
    publishedAt?.let {
        runCatching { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(Instant.parse(it).toEpochMilli())) }
            .getOrNull()
    }
