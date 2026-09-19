package dev.cl0ud9.manager.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow

// a pull-to-refresh that fails (no network, a bad response) used to just stop the spinner with no
// feedback at all - the catalog itself never goes empty on a failure, it falls back to the last
// cache or the bundled seed (RemoteCatalogRepository, section 32), so this is purely telling the
// user their explicit refresh attempt didn't reach the network, not that data is missing
@Composable
fun BoxScope.RefreshFailureSnackbar(
    refreshFailed: Flow<Unit>,
    message: String,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(refreshFailed) {
        refreshFailed.collect { snackbarHostState.showSnackbar(message) }
    }
    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
}
