package dev.cl0ud9.manager.ui.details

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.ui.components.SectionHeader
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.util.pressScale

// stated plainly, before the user ever taps Install: a required dependency (e.g. microG RE for
// Videoapp Patched) that's missing means the app installs "successfully" but never opens - the
// Install button below is already disabled for this case, but a disabled button alone doesn't
// explain *why*, so this spells it out up front with a direct one-tap fix instead of relying on
// the small helper text under the button being read
@Composable
internal fun MissingDependencyWarning(
    unmetDependencies: List<DependencyInfo>,
    onNavigateToApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (unmetDependencies.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "Install ${unmetDependencies.joinToString(" and ") { it.app.displayName }} first",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                text = "Without it, this app will install but won't open. Install it first to avoid that.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            unmetDependencies.forEach { dependency ->
                Button(
                    onClick = { onNavigateToApp(dependency.app.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onErrorContainer,
                            contentColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                ) {
                    Text("Install ${dependency.app.displayName}")
                }
            }
        }
    }
}

// how updates work for this app and which other apps it needs, as two rows of one card - side by
// side as separate cards they could never match in height (a paragraph next to one short line).
// Dependencies are shown with real install state, section 14 + 42.11 of the spec; tapping one opens
// it, so the manager offers a direct path to install it rather than an automatic cascade
@Composable
internal fun AppInfoSection(
    app: AppProfile,
    dependencies: List<DependencyInfo>,
    onNavigateToApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ShapeCache.smooth16,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(
                title = "About this app",
                icon = rememberVectorPainter(Icons.Filled.Info),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            InfoRow(icon = rememberVectorPainter(Icons.Filled.Build), title = "Updates") {
                Text(
                    text = updatesDescription(app.installationMode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA))
            InfoRow(icon = rememberVectorPainter(Icons.Filled.AccountTree), title = "Requires") {
                if (dependencies.isEmpty()) {
                    Text(
                        text = "Nothing else - it works on its own.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        dependencies.forEach { dependency ->
                            DependencyRow(dependency = dependency, onClick = { onNavigateToApp(dependency.app.id) })
                        }
                    }
                }
            }
        }
    }
}

private const val DIVIDER_ALPHA = 0.4f

private fun updatesDescription(mode: InstallationMode): String =
    when (mode) {
        InstallationMode.UPDATE ->
            "Updates install over the current version and keep your data. If one can't, you can " +
                "reinstall from scratch instead, which erases the app's data."
        InstallationMode.CLEAN_INSTALL ->
            "Each update reinstalls the app from scratch: it's uninstalled first, which erases its data."
    }

// a leading icon badge, a title and whatever content belongs under it - an M3 list row
@Composable
private fun InfoRow(
    icon: Painter,
    title: String,
    content: @Composable () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(ShapeCache.smooth12)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun DependencyRow(
    dependency: DependencyInfo,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .pressScale(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = dependency.app.displayName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (dependency.installed) {
            Icon(
                painterResource(R.drawable.ic_check_circle_rounded),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Installed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        } else {
            Text(
                text = "Required, tap to install",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
