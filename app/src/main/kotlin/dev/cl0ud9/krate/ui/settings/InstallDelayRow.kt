package dev.cl0ud9.krate.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.InstallDelay
import dev.cl0ud9.krate.domain.model.label
import dev.cl0ud9.krate.platform.autoupdate.AutoUpdateWorker

// how long a new release waits before installing by itself
@Composable
internal fun InstallDelayRow(
    delay: InstallDelay,
    available: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    SettingsRow(
        header =
            SettingsRowHeader(
                icon = rememberVectorPainter(Icons.Rounded.HourglassTop),
                title = "When to install",
                subtitle =
                    if (available) {
                        "${delay.label()}. Waiting means a broken release is usually pulled before it reaches you."
                    } else {
                        "${delay.label()}, once Install updates automatically is on."
                    },
                colors = SettingsTint.INDIGO.colors(),
            ),
        shape = shape,
        onClick = onClick,
        trailing = { Icon(painterResource(R.drawable.ic_chevron_right_rounded), contentDescription = null) },
    )
}

@Composable
internal fun InstallDelaySheet(
    selected: InstallDelay,
    onDismiss: () -> Unit,
    onSelect: (InstallDelay) -> Unit,
) {
    val icon = rememberVectorPainter(Icons.Rounded.HourglassTop)
    SettingOptionSheet(
        title = "When to install",
        options =
            InstallDelay.entries.map {
                SettingOption(it, icon, it.label(), note = if (it == InstallDelay.DEFAULT) "Recommended" else null)
            },
        selected = selected,
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}

// the sheet saving the choice, in Settings and in setup
@Composable
internal fun InstallDelayPicker(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    InstallDelaySheet(viewModel.installDelay.collectAsStateWithLifecycle().value, onDismiss) { delay ->
        viewModel.setInstallDelay(delay)
        // a shorter wait can make an update due now; a run works out the new times
        AutoUpdateWorker.enqueue(context)
    }
}
