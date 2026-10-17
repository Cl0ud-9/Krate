package dev.cl0ud9.krate.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.latestArtifact
import dev.cl0ud9.krate.platform.autoupdate.UpdateMode
import dev.cl0ud9.krate.ui.components.AppIconAvatar
import dev.cl0ud9.krate.ui.components.UpdateModeLine
import dev.cl0ud9.krate.ui.components.rememberAppIcon
import dev.cl0ud9.krate.ui.components.rememberUpdateMode
import dev.cl0ud9.krate.ui.navigation.IconFrost
import dev.cl0ud9.krate.ui.navigation.LocalLiquidGlass
import dev.cl0ud9.krate.ui.navigation.PageIconSize
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.navigation.sharedDetailsIcon
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.theme.rememberIconAccent

// the app's icon beside its name and package
@Composable
private fun AppIdentity(
    app: AppProfile,
    moment: InstallMoment,
    accent: Color?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InstallHalo(moment, accent) {
            AppIconAvatar(app = app, size = PageIconSize, modifier = Modifier.sharedDetailsIcon(app))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = app.displayName, style = MaterialTheme.typography.headlineSmall)
            Text(
                // a zero-width space after each dot lets a long name wrap between its parts at a big font
                text = app.packageName.replace(".", ".​"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// who the app is, top to bottom: its icon and name, what kind of build it is, what it does, and a footer that says how
// it stands on this phone. Uninstall lives with the other actions in Get this app
@Composable
internal fun AppDetailsHeader(
    app: AppProfile,
    installedVersionName: String?,
    moment: InstallMoment,
) {
    Card(
        modifier = Modifier.fillMaxWidth().glassRim(ShapeCache.rounded16),
        shape = ShapeCache.rounded16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // with glass on the card is frosted by the app's own icon, so its progress and badge take the app's colour too
        val accent =
            if (LocalLiquidGlass.current) {
                rememberIconAccent(rememberAppIcon(app), MaterialTheme.colorScheme.surfaceContainer)
            } else {
                null
            }
        Box {
            IconFrost(rememberAppIcon(app))
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIdentity(app, moment, accent, modifier = Modifier.fillMaxWidth())
                HeaderBadges(app, accent)
                BuildLine(app)
                app.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = FOOTER_DIVIDER_ALPHA))
                InstalledStatusRow(app = app, installedVersionName = installedVersionName)
            }
        }
    }
}

private const val FOOTER_DIVIDER_ALPHA = 0.4f

// only set for an app Krate builds itself: "Latest" above is the app's own version, and this is its build
@Composable
private fun BuildLine(app: AppProfile) {
    val artifact = app.latestArtifact ?: return
    val patches = artifact.patchesVersionName ?: return
    Text(
        text = listOfNotNull(artifact.label, "Build $patches").joinToString(", "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// how the app stands on this phone: that it's there, and how its updates arrive. The two sit apart when there's
// room and stack when there isn't, so a big font never squeezes them together
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstalledStatusRow(
    app: AppProfile,
    installedVersionName: String?,
) {
    val text = installedVersionName?.let { "Installed: version $it" } ?: "Not installed on this device"
    val tint =
        if (installedVersionName != null) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    val mode = rememberUpdateMode(app, installed = installedVersionName != null)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (installedVersionName != null) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle_rounded),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = tint)
        }
        AnimatedVisibility(visible = mode != null, enter = fadeIn() + expandHorizontally(), exit = fadeOut()) {
            // the mode is null as the line fades out; it only has to hold something while it does
            UpdateModeLine(mode ?: UpdateMode.BY_HAND)
        }
    }
}
