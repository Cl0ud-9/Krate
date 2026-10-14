package dev.cl0ud9.krate.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.platform.appContainer
import dev.cl0ud9.krate.platform.autoupdate.UpdateMode
import dev.cl0ud9.krate.platform.autoupdate.krateOwnsUpdates
import dev.cl0ud9.krate.platform.autoupdate.updateMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// how this installed app gets its updates, kept current as the settings change, as the app goes on or off the phone,
// and when the person comes back from somewhere that could have changed who installed it
@Composable
internal fun rememberUpdateMode(
    app: AppProfile,
    installed: Boolean,
): UpdateMode? {
    val context = LocalContext.current
    val global by remember { context.appContainer().autoUpdateStanding.global }.collectAsStateWithLifecycle()
    var visits by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        visits++
        onPauseOrDispose { }
    }
    // Android's record of who installed it, read off the main thread; until it's read, nothing is claimed
    val owns by produceState(false, app.packageName, installed, visits) {
        value = installed && withContext(Dispatchers.IO) { krateOwnsUpdates(context, app.packageName) }
    }
    return updateMode(global, app, installed, owns)
}

// the small mark on an app's row when Krate keeps it up to date by itself
@Composable
internal fun AutoUpdateBadge() {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_update_rounded),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "Auto-updates",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// the line on an app's own page: how its updates arrive, said either way so it's never a guess
@Composable
internal fun UpdateModeLine(mode: UpdateMode) {
    val automatic = mode == UpdateMode.AUTOMATIC
    val tint = if (automatic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_update_rounded),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = if (automatic) "Updates automatically" else "Updates by hand",
            style = MaterialTheme.typography.bodySmall,
            color = tint,
        )
    }
}
