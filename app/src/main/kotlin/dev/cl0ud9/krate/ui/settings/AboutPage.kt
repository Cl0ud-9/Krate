package dev.cl0ud9.krate.ui.settings

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.theme.ShapeCache

private const val SOURCE_URL = "https://github.com/Cl0ud-9/Krate"
private val HERO_ICON_SIZE = 60.dp
private const val PROJECT_ROWS = 2

// who and what Krate is, who makes it, keeping it current, and where it comes from
@Composable
fun AboutPage(
    scrollState: ScrollState,
    topContentPadding: Dp,
) {
    val viewModel = rememberSettingsViewModel()
    val uriHandler = LocalUriHandler.current
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    SettingsPage(scrollState, topContentPadding) {
        AboutHeroCard(versionName = rememberVersionName())
        AboutSectionHeader(title = "Maintainer", subtitle = "The person behind Krate.")
        MaintainerCard()
        AboutSectionHeader(title = "Updates", subtitle = "Krate keeps itself current too.")
        KrateUpdatesCard(viewModel = viewModel)
        AboutSectionHeader(title = "Project", subtitle = "Source available, and built on open-source work.")
        SettingsNavRow(
            icon = painterResource(R.drawable.ic_github),
            title = "Source code",
            subtitle = "github.com/Cl0ud-9/Krate",
            colors = SettingsTint.SLATE.colors(),
            shape = settingsGroupShape(0, PROJECT_ROWS),
            onClick = { uriHandler.openUri(SOURCE_URL) },
        )
        SettingsNavRow(
            icon = painterResource(R.drawable.ic_gavel_rounded),
            title = "Licences",
            subtitle = "Krate's own, and the open-source work it's built on",
            colors = SettingsTint.INDIGO.colors(),
            shape = settingsGroupShape(1, PROJECT_ROWS),
            onClick = { showLicenses = true },
        )
    }
    if (showLicenses) LicensesSheet(onDismiss = { showLicenses = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutHeroCard(versionName: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().glassRim(ShapeCache.rounded28),
        shape = ShapeCache.rounded28,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                KrateIcon()
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "Krate", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Your apps, straight from their releases, always up to date.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(
                    text = "Version $versionName",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AboutTag(icon = R.drawable.ic_public_rounded, label = "Source available")
                AboutTag(icon = R.drawable.ic_gpp_good_rounded, label = "Handpicked apps")
                AboutTag(icon = R.drawable.ic_palette_rounded, label = "Material 3 Expressive")
            }
        }
    }
}

@Composable
private fun AboutTag(
    icon: Int,
    label: String,
) {
    Surface(shape = ShapeCache.rounded16, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun AboutSectionHeader(
    title: String,
    subtitle: String,
) {
    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 22.dp, bottom = 10.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// the app's own launcher icon (adaptive, so read through PackageManager rather than painterResource)
@Composable
private fun KrateIcon() {
    val context = LocalContext.current
    val painter =
        remember {
            runCatching { context.packageManager.getApplicationIcon(context.packageName) }
                .getOrNull()
                ?.let(Drawable::toBitmap)
                ?.let { BitmapPainter(it.asImageBitmap()) }
        }
    if (painter != null) {
        Image(painter = painter, contentDescription = null, modifier = Modifier.size(HERO_ICON_SIZE).clip(CircleShape))
    }
}
