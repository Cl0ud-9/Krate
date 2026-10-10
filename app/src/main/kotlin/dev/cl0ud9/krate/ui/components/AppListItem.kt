package dev.cl0ud9.krate.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.DownloadStatus
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.domain.model.SupportStatus
import dev.cl0ud9.krate.domain.model.fromGitHub
import dev.cl0ud9.krate.domain.model.isTracked
import dev.cl0ud9.krate.domain.model.latestVersionName
import dev.cl0ud9.krate.platform.appContainer
import dev.cl0ud9.krate.platform.work.AppWorkState
import dev.cl0ud9.krate.ui.navigation.SharedIconOrigin
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.navigation.sharedAppIcon
import dev.cl0ud9.krate.ui.theme.ShapeCache
import dev.cl0ud9.krate.ui.util.pressScale

// name, newest version and status only; the description lives on the app's own page, keeping the list easy to scan
@Composable
fun AppListItem(
    app: AppProfile,
    installed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // where this list sits, so the app's icon can fly from it into the app's page
    place: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .pressScale(interactionSource)
                .clip(ShapeCache.rounded20)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = {
                        if (place != null) SharedIconOrigin.tapped(app.id, place)
                        onClick()
                    },
                ).glassRim(ShapeCache.rounded20),
        shape = ShapeCache.rounded20,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIconAvatar(
                app = app,
                modifier = if (place != null) Modifier.sharedAppIcon(app.id, place) else Modifier,
            )

            AppRowText(app = app, installed = installed, modifier = Modifier.weight(1f))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// the name, then what's under way for the app or its newest version, then its badges
@Composable
private fun AppRowText(
    app: AppProfile,
    installed: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = app.displayName, style = MaterialTheme.typography.titleMedium)
        // what's under way for it, carried on from its page, or else the catalog's newest version
        val work = rememberAppWork(app.id)
        val progress = workProgress(work)
        RowStatusLine(
            line = workLine(work),
            idle = app.latestVersionName?.let { "Latest $it" } ?: "Not available yet",
            waiting = progress != null && progress < 0f,
        )
        RowBar(fill = progress?.takeIf { it >= 0f })
        RowBadges(app = app, installed = installed)
    }
}

// the badges under the line; installing or removing the app grows or folds the row smoothly rather than in one frame
@Composable
private fun RowBadges(
    app: AppProfile,
    installed: Boolean,
) {
    AnimatedVisibility(
        visible = installed || app.isTracked || app.supportStatus != SupportStatus.SUPPORTED,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            SupportStatusBadge(status = app.supportStatus)
            AnimatedVisibility(
                visible = installed,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) { InstalledBadge() }
            if (app.isTracked) TrackedBadge(onGitHub = app.fromGitHub)
        }
    }
}

// the line under the name; it fades only when the kind of work changes, so a percentage counts up in place. Work at
// no knowable pace (checking, installing, a size that never came) turns a small loader beside it, the same loader the
// app's page shows, rather than a bar with nothing behind it
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowStatusLine(
    line: String?,
    idle: String,
    waiting: Boolean,
) {
    val style = MaterialTheme.typography.bodySmall.withTabularFigures()
    // the loader is one line tall at any font size, and the line always keeps room for it, so the row is the same
    // height with or without it and doesn't drop back when it goes
    val loaderSize = with(LocalDensity.current) { style.lineHeight.toDp() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp).heightIn(min = loaderSize),
    ) {
        AnimatedVisibility(
            visible = waiting,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            LoadingIndicator(modifier = Modifier.padding(end = 6.dp).size(loaderSize))
        }
        AnimatedContent(
            targetState = line,
            contentKey = ::workPhase,
            // out, then in, like every other status swap, so two lines never overlap mid-fade
            transitionSpec = fadeThrough(),
            label = "row-status",
        ) { shown ->
            Text(
                text = shown ?: idle,
                style = style,
                color =
                    if (shown !=
                        null
                    ) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

// the download's bar under the line: folds in and out with it, and glides between steps like every other bar
@Composable
private fun RowBar(fill: Float?) {
    // the last share seen, so the bar keeps it while it folds away
    val last = remember { floatArrayOf(0f) }
    if (fill != null) last[0] = fill
    AnimatedVisibility(
        visible = fill != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Box(modifier = Modifier.padding(top = 6.dp)) { KrateLinearProgress(progress = fill ?: last[0]) }
    }
}

@Composable
private fun InstalledBadge() {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_check_circle_rounded),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "Installed",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun rememberAppWork(appId: String): AppWorkState {
    val context = LocalContext.current
    val flow = remember(appId) { context.appContainer().appWork.state(appId) }
    val state by flow.collectAsStateWithLifecycle(initialValue = AppWorkState())
    return state
}

// the row's line while something is under way for the app; null when nothing is
internal fun workLine(work: AppWorkState): String? {
    val download = work.download
    return when {
        work.install is InstallStatus.Uninstalling -> "Uninstalling..."
        work.install is InstallStatus.WaitingForUser -> "Waiting for your OK..."
        work.install == InstallStatus.Installing || work.install == InstallStatus.PreparingRollback -> "Installing..."
        download is DownloadStatus.Downloading -> {
            val total = download.totalBytes
            if (total != null &&
                total > 0
            ) {
                "Downloading ${download.bytesDownloaded * PERCENT / total}%"
            } else {
                "Downloading..."
            }
        }
        download is DownloadStatus.Verifying -> "Checking the download..."
        else -> null
    }
}

// which kind of work the line is about, so "Downloading 25%" and "26%" count as one and only a new kind fades
internal fun workPhase(line: String?): String? = line?.takeWhile { it.isLetter() }

// how far along: a share for the bar under the line, -1 for work at no knowable pace (a small loader instead), null for
// nothing under way
internal fun workProgress(work: AppWorkState): Float? {
    val download = work.download
    val line = workLine(work)
    return when {
        line == null -> null
        // only while the line is about the download, so an install never sits over a full bar
        line.startsWith("Downloading") && download is DownloadStatus.Downloading ->
            downloadFraction(download.bytesDownloaded, download.totalBytes) ?: -1f
        else -> -1f
    }
}

private const val PERCENT = 100
