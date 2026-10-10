package dev.cl0ud9.krate.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.rememberDebouncedButtonState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private const val MAINTAINER_NAME = "Cloud/9"
private const val MAINTAINER_LOGIN = "Cl0ud-9"
private const val MAINTAINER_URL = "https://github.com/$MAINTAINER_LOGIN"
private const val AVATAR_URL = "https://github.com/$MAINTAINER_LOGIN.png?size=160"
private const val AVATAR_FILE = "maintainer_avatar.png"
private val AVATAR_MAX_AGE_MS = TimeUnit.DAYS.toMillis(7)
private val AVATAR_SIZE = 60.dp

@Composable
internal fun MaintainerCard() {
    val uriHandler = LocalUriHandler.current
    Surface(
        modifier = Modifier.fillMaxWidth().glassRim(ShapeCache.rounded24),
        shape = ShapeCache.rounded24,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MaintainerAvatar()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = MAINTAINER_NAME,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Creator and maintainer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Building Krate for friends, shaped by what they ask for.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledIconButton(
                onClick = { uriHandler.openUri(MAINTAINER_URL) },
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
            ) {
                Icon(
                    painterResource(R.drawable.ic_github),
                    contentDescription = "$MAINTAINER_NAME on GitHub",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// the maintainer's GitHub avatar, cached for a week; their initial until it arrives or when offline
@Composable
private fun MaintainerAvatar() {
    val context = LocalContext.current
    val avatar by produceState<Bitmap?>(initialValue = null) {
        value =
            withContext(Dispatchers.IO) { loadAvatar(context) }
    }
    val current = avatar
    if (current != null) {
        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(AVATAR_SIZE).clip(CircleShape),
        )
    } else {
        Box(modifier = Modifier.size(AVATAR_SIZE).clip(CircleShape), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(AVATAR_SIZE),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = MAINTAINER_NAME.take(1),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

private fun loadAvatar(context: Context): Bitmap? {
    val file = File(context.cacheDir, AVATAR_FILE)
    val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < AVATAR_MAX_AGE_MS
    if (!fresh) {
        runCatching {
            OkHttpClient().newCall(Request.Builder().url(AVATAR_URL).build()).execute().use { response ->
                val bytes = response.body?.bytes()
                if (response.isSuccessful && bytes != null) file.writeBytes(bytes)
            }
        }
    }
    return runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
}

// Krate's own update status and actions, as one card
@Composable
internal fun KrateUpdatesCard(viewModel: SettingsViewModel) {
    val krateUpdateState by viewModel.krateUpdateState.collectAsStateWithLifecycle()
    val selfUpdateState by viewModel.selfUpdateState.collectAsStateWithLifecycle()
    val checkForUpdateState = rememberDebouncedButtonState(onClick = { viewModel.checkForKrateUpdate() })
    Surface(
        modifier = Modifier.fillMaxWidth().glassRim(ShapeCache.rounded24),
        shape = ShapeCache.rounded24,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = ShapeCache.rounded16, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        painterResource(R.drawable.ic_krate),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp).size(24.dp),
                    )
                }
                Column {
                    Text(
                        text = "Krate updates",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "You have version ${rememberVersionName()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            KrateUpdateSection(
                state = krateUpdateState,
                actions = KrateUpdateActions(checkForUpdateState, selfUpdateState, viewModel::installKrateUpdate),
            )
        }
    }
}
