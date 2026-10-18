package dev.cl0ud9.krate.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
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
import dev.cl0ud9.krate.domain.model.DownloadStatus
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.domain.model.WaitingForUserStep
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
import dev.cl0ud9.krate.ui.navigation.rememberGlassRimBorder
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

// who the app is, top to bottom: its icon and name with Uninstall in the corner, what kind of build it is, what it
// does, and a footer that says how it stands on this phone
@Composable
internal fun AppDetailsHeader(
    app: AppProfile,
    installedVersionName: String?,
    uninstall: UninstallState,
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
                Row(verticalAlignment = Alignment.Top) {
                    AppIdentity(app, moment, accent, modifier = Modifier.weight(1f))
                    // fades and folds away once the app is gone, rather than leaving a jump in the row
                    AnimatedVisibility(
                        visible = installedVersionName != null,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) { UninstallControl(uninstall) }
                }
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

// the way out, in the header's corner where it was always found: a small button with the word on it so nobody has to
// guess what the icon does. With glass on it's a tinted pane with a thin red rim, so it sits in the frosted card
// instead of punching a solid patch into it; otherwise the error container's colours. While the uninstall runs it
// keeps its place, turns to a spinner and "Uninstalling", and can't be pressed again
// what the control needs: whether an uninstall is running, whether an install or download is (so the two never cross),
// and what a press does
internal data class UninstallState(
    val running: Boolean,
    val locked: Boolean,
    val onClick: () -> Unit,
)

// an update downloading, being checked or installing: removing the app under it would only make both fail
internal fun installBusy(state: AppDetailsUiState): Boolean =
    state.downloadStatus is DownloadStatus.Downloading ||
        state.downloadStatus is DownloadStatus.Verifying ||
        state.installStatus is InstallStatus.Installing ||
        state.installStatus is InstallStatus.PreparingRollback ||
        state.installStatus is InstallStatus.RollingBack ||
        (
            state.installStatus is InstallStatus.WaitingForUser &&
                state.installStatus.step == WaitingForUserStep.INSTALL_CONFIRM
        )

// the way out, in the header's corner where it was always found: a small solid button with the word on it so nobody has
// to guess what the icon does. Solid on purpose: the card behind it takes its colour from the app's icon, and red
// glass over green or purple turns muddy, while the error container reads the same over any of them. With glass on it
// gets the same thin light rim as the cards around it. While the uninstall runs it keeps its place, turns to a spinner
// and "Uninstalling", and can't be pressed again
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UninstallControl(uninstall: UninstallState) {
    val colors = MaterialTheme.colorScheme
    // working, it keeps its full colour so the spinner and the word don't go grey; held back for an install it dims
    val held = uninstall.locked && !uninstall.running
    FilledTonalButton(
        onClick = uninstall.onClick,
        enabled = !uninstall.running && !uninstall.locked,
        shape = ShapeCache.pill,
        modifier = Modifier.height(UNINSTALL_HEIGHT).animateContentSize(),
        contentPadding = PaddingValues(horizontal = 14.dp),
        border = rememberGlassRimBorder(),
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = colors.errorContainer,
                contentColor = colors.onErrorContainer,
                disabledContainerColor = colors.errorContainer.copy(alpha = if (held) HELD_ALPHA else 1f),
                disabledContentColor = colors.onErrorContainer.copy(alpha = if (held) HELD_ALPHA else 1f),
            ),
    ) {
        if (uninstall.running) {
            LoadingIndicator(color = colors.onErrorContainer, modifier = Modifier.size(18.dp))
        } else {
            Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = if (uninstall.running) "Uninstalling" else "Uninstall", style = MaterialTheme.typography.labelLarge)
    }
}

private val UNINSTALL_HEIGHT = 36.dp
private const val HELD_ALPHA = 0.38f

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
