package dev.cl0ud9.manager.ui.settings

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.ui.components.ChangelogSheet
import dev.cl0ud9.manager.ui.theme.ShapeCache

private const val SOURCE_URL = "https://github.com/Cl0ud-9/manager"
private val HERO_ICON_SIZE = 64.dp

// who and what this is up top, then everything about keeping it current, then where it comes from
@Composable
fun AboutPage(
    scrollState: ScrollState,
    topContentPadding: Dp,
) {
    val viewModel = rememberSettingsViewModel()
    val uriHandler = LocalUriHandler.current
    var showChangelog by remember { mutableStateOf(false) }
    SettingsPage(scrollState, topContentPadding) {
        AboutHeroCard(versionName = rememberVersionName())
        SettingsSectionLabel("Updates")
        SettingsAboutRow(viewModel = viewModel, shape = settingsGroupShape(0, 2))
        SettingsNavRow(
            icon = painterResource(R.drawable.ic_newspaper_rounded),
            title = "What's new",
            subtitle = "Release notes for recent versions",
            colors = defaultSettingsRowColors(),
            shape = settingsGroupShape(1, 2),
            onClick = { showChangelog = true },
        )
        SettingsSectionLabel("Project")
        SettingsNavRow(
            icon = rememberVectorPainter(Icons.Filled.Code),
            title = "Source code",
            subtitle = "github.com/Cl0ud-9/manager",
            colors =
                SettingsRowColors(
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            shape = settingsGroupShape(0, 1),
            onClick = { uriHandler.openUri(SOURCE_URL) },
        )
    }
    if (showChangelog) {
        ChangelogSheet(onDismiss = { showChangelog = false })
    }
}

@Composable
private fun AboutHeroCard(versionName: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.smooth28,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ManagerIcon()
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "App Manager",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Install your apps and keep them up to date, straight from their releases.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Text(
                    text = "Version $versionName",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

// the app's own launcher icon (adaptive, so read through PackageManager rather than painterResource)
@Composable
private fun ManagerIcon() {
    val context = LocalContext.current
    val painter =
        remember {
            runCatching { context.packageManager.getApplicationIcon(context.packageName) }
                .getOrNull()
                ?.let(Drawable::toBitmap)
                ?.let { BitmapPainter(it.asImageBitmap()) }
        }
    if (painter != null) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(HERO_ICON_SIZE).clip(CircleShape),
        )
    }
}
