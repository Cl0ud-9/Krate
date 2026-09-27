package dev.cl0ud9.krate.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.ui.theme.KrateRounded
import dev.cl0ud9.krate.ui.theme.KrateWideDisplay
import dev.cl0ud9.krate.ui.util.isShortScreen

// the cover page: a wide "Welcome to", the Krate name in brand color, the crate art, and what's next;
// on a phone on its side the words and the art sit side by side
@Composable
internal fun WelcomePage() {
    if (isShortScreen()) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WelcomeHeading()
                WelcomeNote(Modifier)
            }
            KrateIllustration(modifier = Modifier.weight(1f))
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Box(modifier = Modifier.padding(horizontal = 8.dp).padding(top = 12.dp)) { WelcomeHeading() }
        KrateIllustration()
        WelcomeNote(Modifier.fillMaxWidth().padding(bottom = 8.dp))
    }
}

@Composable
private fun WelcomeHeading() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "Welcome to", style = KrateWideDisplay)
        Text(
            text = "Krate",
            style =
                MaterialTheme.typography.displayLarge.copy(
                    fontFamily = KrateRounded,
                    fontSize = 46.sp,
                    lineHeight = 1.1.em,
                    color = MaterialTheme.colorScheme.primary,
                ),
        )
    }
}

@Composable
private fun WelcomeNote(modifier: Modifier) {
    Text(
        text = "Let's get everything set up for you.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = if (isShortScreen()) TextAlign.Start else TextAlign.Center,
        modifier = modifier,
    )
}

// a grant button: its label while there's something to do, a check once it's done
internal class PageAction(
    val label: String,
    val doneLabel: String,
    val done: Boolean,
    val onClick: () -> Unit,
)

// every step's layout: centered title and explanation up top, the icon collage in the middle, and its
// own controls (or grant button) at the bottom; side by side on a phone on its side
@Composable
internal fun StepPageLayout(
    title: String,
    description: String,
    @DrawableRes icons: List<Int>?,
    action: PageAction? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    if (isShortScreen()) {
        SideBySide(
            words = {
                StepHeading(title, description, TextAlign.Start)
                StepControls(action, content)
            },
            art = { if (icons != null) OnboardingCollage(icons = icons) },
        )
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(16.dp))
            StepHeading(title, description, TextAlign.Center)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (icons != null) OnboardingCollage(icons = icons)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            StepControls(action, content)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// words on the left (scrolling if they run long), the page's art on the right
@Composable
internal fun SideBySide(
    words: @Composable ColumnScope.() -> Unit,
    art: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            content = words,
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { art() }
    }
}

@Composable
private fun StepHeading(
    title: String,
    description: String,
    align: TextAlign,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.displayMedium.copy(fontSize = 32.sp, lineHeight = 40.sp),
        textAlign = align,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = description,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = align,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.StepControls(
    action: PageAction?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Spacer(modifier = Modifier.height(16.dp))
    content()
    if (action != null) {
        Spacer(modifier = Modifier.height(16.dp))
        GrantButton(action)
    }
}

@Composable
private fun GrantButton(action: PageAction) {
    Button(
        onClick = action.onClick,
        enabled = !action.done,
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
    ) {
        AnimatedContent(targetState = action.done, label = "grant") { done ->
            if (done) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_check_rounded),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(action.doneLabel, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Text(action.label, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// the finish page's Krate tile, handed to the intro that plays once setup is done
internal class MarkHandoff(
    val hidden: Boolean,
    val onPlaced: (Rect) -> Unit,
)

internal val INSTALL_ICONS =
    listOf(
        R.drawable.ic_security_rounded,
        R.drawable.ic_verified_user_rounded,
        R.drawable.ic_lock_rounded,
        R.drawable.ic_gpp_good_rounded,
        R.drawable.ic_shield_rounded,
    )
internal val NOTIFICATION_ICONS =
    listOf(
        R.drawable.ic_notifications_active_rounded,
        R.drawable.ic_stat_krate,
        R.drawable.ic_nav_update_filled,
        R.drawable.ic_campaign_rounded,
        R.drawable.ic_notifications_none_rounded,
    )
internal val PLAY_PROTECT_ICONS =
    listOf(
        R.drawable.ic_shield_rounded,
        R.drawable.ic_gpp_good_rounded,
        R.drawable.ic_policy_rounded,
        R.drawable.ic_verified_user_rounded,
        R.drawable.ic_krate,
    )
internal val UPDATES_ICONS =
    listOf(
        R.drawable.ic_nav_update_filled,
        R.drawable.ic_update_rounded,
        R.drawable.ic_stat_krate,
        R.drawable.ic_nav_apps_filled,
        R.drawable.ic_check_rounded,
    )
