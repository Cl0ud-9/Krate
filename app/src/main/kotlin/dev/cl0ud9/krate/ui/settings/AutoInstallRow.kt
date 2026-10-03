package dev.cl0ud9.krate.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.ui.components.KrateSwitch

// installs downloaded updates by themselves; it needs Automatic downloads, which fetch what it installs
@Composable
internal fun AutoInstallRow(
    checked: Boolean,
    available: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: Shape,
) {
    SettingsRow(
        header =
            SettingsRowHeader(
                icon = painterResource(R.drawable.ic_system_update_alt_rounded),
                title = "Install updates automatically",
                subtitle =
                    if (available) {
                        "Updates to apps from Krate install a day after release, while you're not using them. " +
                            "It never rolls an app back or erases its data."
                    } else {
                        "Turn on Automatic downloads to use this."
                    },
                colors = SettingsTint.BLUE.colors(),
            ),
        shape = shape,
        trailing = {
            KrateSwitch(checked = checked && available, onCheckedChange = onCheckedChange, enabled = available)
        },
    )
}
