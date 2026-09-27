package dev.cl0ud9.manager.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.ui.theme.ShapeCache
import kotlinx.coroutines.flow.Flow

// a short-lived message: a headline, the plain fact under it, an icon for the kind of news, and an optional action
class KrateMessage(
    val headline: String,
    val detail: String,
    @param:DrawableRes val icon: Int,
    override val actionLabel: String? = null,
) : SnackbarVisuals {
    override val message: String get() = "$headline $detail"
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Short
}

// a failed pull-to-refresh says so, offering to try again - what's already on screen stays as it was
@Composable
fun BoxScope.RefreshFailureSnackbar(
    refreshFailed: Flow<Unit>,
    message: () -> KrateMessage,
    onRetry: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val latestMessage = rememberUpdatedState(message)
    val latestRetry = rememberUpdatedState(onRetry)
    LaunchedEffect(refreshFailed) {
        refreshFailed.collect {
            val result = snackbarHostState.showSnackbar(latestMessage.value())
            if (result == SnackbarResult.ActionPerformed) latestRetry.value()
        }
    }
    KrateSnackbarHost(
        state = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = LocalNavBarClearance.current),
    )
}

@Composable
fun KrateSnackbarHost(
    state: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = state, modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { data ->
        KrateSnackbar(data)
    }
}

@Composable
private fun KrateSnackbar(data: SnackbarData) {
    val visuals = data.visuals
    val krate = visuals as? KrateMessage
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.smooth24,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(
                    painter = painterResource(krate?.icon ?: R.drawable.ic_info_rounded),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(9.dp).size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = krate?.headline ?: visuals.message,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (krate != null) {
                    Text(
                        text = krate.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            visuals.actionLabel?.let { label ->
                TextButton(onClick = data::performAction) { Text(label, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
