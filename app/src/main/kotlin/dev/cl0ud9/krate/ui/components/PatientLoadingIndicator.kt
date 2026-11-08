package dev.cl0ud9.krate.ui.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

// how long loading has to take before the loader shows
private const val LOADER_DELAY_MS = 300L

// Material's loader, once loading has gone on long enough to notice: a page or list that's ready in a moment never
// flashes one, and its opening frames skip one of the dearest things there is to put on screen
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PatientLoadingIndicator(modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADER_DELAY_MS)
        shown = true
    }
    if (shown) LoadingIndicator(modifier = modifier)
}
