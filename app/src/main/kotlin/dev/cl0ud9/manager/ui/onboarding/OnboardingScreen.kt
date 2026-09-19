package dev.cl0ud9.manager.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.R
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.util.RefreshOnResume
import dev.cl0ud9.manager.ui.util.managerViewModel

private enum class OnboardingStep {
    WELCOME,
    INSTALL_UNKNOWN_APPS,
    NOTIFICATION_PERMISSION,
    PLAY_PROTECT_NOTICE,
}

private const val ACTION_MORPH_MS = 500
private const val ACTION_ROTATE_MS = 700
private const val STEP_FADE_MS = 220
private const val FULL_ROTATION_DEGREES = 360f
private const val CIRCLE_CORNER_PERCENT = 50f
private const val SQUARE_CORNER_PERCENT = 26f
private const val LEAF_MAJOR_CORNER_PERCENT = 50f
private const val LEAF_MINOR_CORNER_PERCENT = 18f
private const val SHAPE_CYCLE_LENGTH = 3
private const val STEP_SLIDE_DIVISOR = 4

private data class CornerPercents(
    val topStart: Float,
    val topEnd: Float,
    val bottomStart: Float,
    val bottomEnd: Float,
)

private fun cornerPercentsFor(step: Int): CornerPercents =
    when (step % SHAPE_CYCLE_LENGTH) {
        0 -> CornerPercents(CIRCLE_CORNER_PERCENT, CIRCLE_CORNER_PERCENT, CIRCLE_CORNER_PERCENT, CIRCLE_CORNER_PERCENT)
        1 -> CornerPercents(SQUARE_CORNER_PERCENT, SQUARE_CORNER_PERCENT, SQUARE_CORNER_PERCENT, SQUARE_CORNER_PERCENT)
        else ->
            // opposite corners rounded (diagonal pairing), not matching sides - that's what
            // actually reads as a leaf/teardrop silhouette instead of a lopsided rounded rectangle
            CornerPercents(
                LEAF_MAJOR_CORNER_PERCENT,
                LEAF_MINOR_CORNER_PERCENT,
                LEAF_MINOR_CORNER_PERCENT,
                LEAF_MAJOR_CORNER_PERCENT,
            )
    }

// the notification-permission step only applies on API 33+, where the runtime permission exists
// at all - pulled out of OnboardingScreen itself purely to keep that composable under detekt's
// method-length threshold
private fun onboardingStepsForDevice(): List<OnboardingStep> =
    buildList {
        add(OnboardingStep.WELCOME)
        add(OnboardingStep.INSTALL_UNKNOWN_APPS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(OnboardingStep.NOTIFICATION_PERMISSION)
        add(OnboardingStep.PLAY_PROTECT_NOTICE)
    }

// runs once before the Apps catalog is reachable, amendment 44.4 of the spec: install-unknown-apps
// grant, notification permission, and a Play Protect heads-up are anticipated here instead of being
// discovered mid-deployment
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val viewModel = managerViewModel { container -> OnboardingViewModel(container.settingsRepository) }
    val context = LocalContext.current

    val steps = remember { onboardingStepsForDevice() }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }

    var canInstallUnknownApps by remember { mutableStateOf(context.packageManager.canRequestPackageInstalls()) }
    // the user grants this in a separate Settings screen, not a system dialog - re-check whenever
    // this screen resumes, the same pattern used elsewhere in the app for device-local state
    RefreshOnResume { canInstallUnknownApps = context.packageManager.canRequestPackageInstalls() }

    val installSourceLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            canInstallUnknownApps = context.packageManager.canRequestPackageInstalls()
        }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            // no-op: notifications are a nice-to-have (WorkManager is the fallback, section 44.4/24),
            // not a hard requirement, so Continue is never blocked on the result here
        }

    val onContinue: () -> Unit = {
        if (stepIndex < steps.lastIndex) {
            stepIndex++
        } else {
            viewModel.completeOnboarding()
            onComplete()
        }
    }

    val actions =
        remember(installSourceLauncher, notificationPermissionLauncher) {
            OnboardingActions(
                onOpenInstallSettings = {
                    installSourceLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                    )
                },
                onRequestNotifications = {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            )
        }

    Column(modifier = Modifier.fillMaxSize()) {
        OnboardingStepPager(
            steps = steps,
            stepIndex = stepIndex,
            canInstallUnknownApps = canInstallUnknownApps,
            actions = actions,
            modifier = Modifier.weight(1f),
        )

        val canContinue = steps[stepIndex] != OnboardingStep.INSTALL_UNKNOWN_APPS || canInstallUnknownApps
        OnboardingBottomBar(
            stepIndex = stepIndex,
            totalSteps = steps.size,
            canContinue = canContinue,
            onContinue = onContinue,
        )
    }
}

// the two permission-request callbacks travel together everywhere StepBody is reachable from -
// bundling them keeps OnboardingStepPager under detekt's parameter-count threshold without losing
// either callback's own name at the call site
private data class OnboardingActions(
    val onOpenInstallSettings: () -> Unit,
    val onRequestNotifications: () -> Unit,
)

@Composable
private fun OnboardingStepPager(
    steps: List<OnboardingStep>,
    stepIndex: Int,
    canInstallUnknownApps: Boolean,
    actions: OnboardingActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedContent(
            targetState = stepIndex,
            label = "onboarding-step",
            transitionSpec = {
                val enter =
                    slideInHorizontally(animationSpec = tween(STEP_FADE_MS)) { it / STEP_SLIDE_DIVISOR } +
                        fadeIn(tween(STEP_FADE_MS))
                val exit =
                    slideOutHorizontally(animationSpec = tween(STEP_FADE_MS)) { -it / STEP_SLIDE_DIVISOR } +
                        fadeOut(tween(STEP_FADE_MS))
                enter.togetherWith(exit)
            },
        ) { index ->
            StepBody(
                step = steps[index],
                canInstallUnknownApps = canInstallUnknownApps,
                onOpenInstallSettings = actions.onOpenInstallSettings,
                onRequestNotifications = actions.onRequestNotifications,
            )
        }
    }
}

// a rounded-top sheet instead of a plain full-width button floating on the background: an animated
// step counter on the left (slides vertically as it changes, matching the direction of travel) and a
// circular action button on the right whose shape morphs between three silhouettes and rotates a full
// turn on every step - small, repeated, on-brand delight rather than a flat "Continue" every time
@Composable
private fun OnboardingBottomBar(
    stepIndex: Int,
    totalSteps: Int,
    canContinue: Boolean,
    onContinue: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.contentPanel(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = stepIndex,
                label = "onboarding-step-label",
                transitionSpec = {
                    (slideInVertically(animationSpec = tween(STEP_FADE_MS)) { it } + fadeIn(tween(STEP_FADE_MS)))
                        .togetherWith(
                            slideOutVertically(animationSpec = tween(STEP_FADE_MS)) { -it } +
                                fadeOut(tween(STEP_FADE_MS)),
                        )
                },
            ) { index ->
                Text(
                    text =
                        if (index == totalSteps - 1) {
                            "Get started"
                        } else {
                            "Step ${index + 1} of $totalSteps"
                        },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            MorphingActionButton(step = stepIndex, enabled = canContinue, onClick = onContinue)
        }
    }
}

@Composable
private fun MorphingActionButton(
    step: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // three silhouettes to cycle through - circle, rounded square, "leaf" (opposite corners rounded) -
    // percent-based RoundedCornerShape morphs smoothly between them as the target percentages animate
    val target = cornerPercentsFor(step)
    val topStart by animateFloatAsState(target.topStart, tween(ACTION_MORPH_MS), label = "topStart")
    val topEnd by animateFloatAsState(target.topEnd, tween(ACTION_MORPH_MS), label = "topEnd")
    val bottomStart by animateFloatAsState(target.bottomStart, tween(ACTION_MORPH_MS), label = "bottomStart")
    val bottomEnd by animateFloatAsState(target.bottomEnd, tween(ACTION_MORPH_MS), label = "bottomEnd")
    val rotation by
        animateFloatAsState(
            targetValue = step * FULL_ROTATION_DEGREES,
            animationSpec = tween(ACTION_ROTATE_MS),
            label = "actionRotation",
        )

    val containerColor =
        if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor =
        if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier =
            Modifier
                .size(56.dp)
                .clip(
                    RoundedCornerShape(
                        topStartPercent = topStart.toInt(),
                        topEndPercent = topEnd.toInt(),
                        bottomStartPercent = bottomStart.toInt(),
                        bottomEndPercent = bottomEnd.toInt(),
                    ),
                ).background(containerColor)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_forward_rounded),
            contentDescription = if (step == 0) "Continue" else "Next",
            tint = contentColor,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
    }
}

@Composable
private fun StepBody(
    step: OnboardingStep,
    canInstallUnknownApps: Boolean,
    onOpenInstallSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    when (step) {
        OnboardingStep.WELCOME -> {
            WelcomeStepContent()
        }

        OnboardingStep.INSTALL_UNKNOWN_APPS -> {
            OnboardingStepContent(
                icons = INSTALL_STEP_ICONS,
                title = "Allow installs from this app",
                body =
                    "App Manager installs and updates apps directly, the same way an app store " +
                        "would. Android requires a one-time permission for that.",
                action =
                    OnboardingAction(
                        text = if (canInstallUnknownApps) "Permission granted" else "Open settings",
                        granted = canInstallUnknownApps,
                        onClick = onOpenInstallSettings,
                    ),
            )
        }

        OnboardingStep.NOTIFICATION_PERMISSION -> {
            OnboardingStepContent(
                icons = NOTIFICATION_STEP_ICONS,
                title = "Stay notified about updates",
                body = "Get notified when a new version is available for one of your apps.",
                action =
                    OnboardingAction(text = "Allow notifications", granted = false, onClick = onRequestNotifications),
            )
        }

        OnboardingStep.PLAY_PROTECT_NOTICE -> {
            OnboardingStepContent(
                icons = PLAY_PROTECT_STEP_ICONS,
                title = "About Play Protect warnings",
                body =
                    "Some apps in the catalog are signed with this project's own key instead " +
                        "of Google's. Play Protect may warn when installing them - that's " +
                        "expected, not a sign of a broken build.",
            )
        }
    }
}

private val INSTALL_STEP_ICONS =
    listOf(
        R.drawable.ic_security_rounded,
        R.drawable.ic_verified_user_rounded,
        R.drawable.ic_lock_rounded,
        R.drawable.ic_gpp_good_rounded,
    )
private val NOTIFICATION_STEP_ICONS =
    listOf(
        R.drawable.ic_notifications_rounded,
        R.drawable.ic_notifications_active_rounded,
        R.drawable.ic_notifications_none_rounded,
        R.drawable.ic_campaign_rounded,
    )
private val PLAY_PROTECT_STEP_ICONS =
    listOf(
        R.drawable.ic_shield_rounded,
        R.drawable.ic_gpp_good_rounded,
        R.drawable.ic_policy_rounded,
        R.drawable.ic_verified_user_rounded,
    )

// bundles the CTA button's three related values into one, purely to keep OnboardingStepContent
// under detekt's parameter-count threshold without losing each value's own name at the call site
private data class OnboardingAction(
    val text: String,
    val granted: Boolean,
    val onClick: () -> Unit,
)

// centered, full-bleed hero layout instead of a compact left-aligned icon chip - a scattered
// collage of related glyphs behind the title gives each step its own visual identity instead of
// every page reading as the same icon-title-body-button template
@Composable
private fun OnboardingStepContent(
    icons: List<Int>,
    title: String,
    body: String,
    action: OnboardingAction? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OnboardingIconCollage(icons = icons, modifier = Modifier.fillMaxWidth())
        if (action != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = action.onClick,
                enabled = !action.granted,
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
            ) {
                AnimatedContent(targetState = action.granted, label = "onboarding-button-state") { isGranted ->
                    if (isGranted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(R.drawable.ic_check_rounded),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(action.text, style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        Text(action.text, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

// four related glyphs scattered around a shared center point, rotated and tinted from the theme's
// accent colors - stands in for a plain static icon badge with something that fills the page's
// empty middle section and gives each step a distinct silhouette
@Composable
private fun OnboardingIconCollage(
    icons: List<Int>,
    modifier: Modifier = Modifier,
) {
    val chipColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val palette =
        CollagePalette(
            primary = MaterialTheme.colorScheme.primary,
            secondary = MaterialTheme.colorScheme.secondary,
            tertiary = MaterialTheme.colorScheme.tertiary,
            onSurfaceMuted = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    val smooth20 = ShapeCache.smooth20
    val painters = icons.map { painterResource(it) }
    val configs = remember(painters, palette, smooth20) { collageSlotsFor(painters, palette, smooth20) }
    BoxWithConstraints(modifier = modifier.height(CollageSize.Height)) {
        val edgeInset = maxWidth * CollageSize.EDGE_INSET_FRACTION
        configs.forEach { slot ->
            Box(
                modifier =
                    Modifier
                        .align(slot.alignment)
                        .padding(horizontal = edgeInset, vertical = 8.dp)
                        .size(slot.size)
                        .graphicsLayer { rotationZ = slot.rotation }
                        .clip(slot.shape)
                        .background(chipColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = slot.icon,
                    contentDescription = null,
                    tint = slot.tint,
                    modifier = Modifier.size(slot.size * CollageSize.GLYPH_SCALE),
                )
            }
        }
    }
}

// bundles the collage's four accent colors into one, purely to keep collageSlotsFor under
// detekt's parameter-count threshold without losing each color's own name at the call site
private data class CollagePalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val onSurfaceMuted: Color,
)

// pulled out of OnboardingIconCollage itself purely to keep that composable under detekt's
// method-length threshold - this part has no Compose state/side effects of its own, just data
private fun collageSlotsFor(
    icons: List<Painter>,
    palette: CollagePalette,
    smooth20: Shape,
): List<CollageIconSlot> =
    listOf(
        CollageIconSlot(
            icons[0],
            CollageSize.Center,
            Alignment.Center,
            CollageRotation.CENTER,
            smooth20,
            palette.primary,
        ),
        CollageIconSlot(
            icons[1],
            CollageSize.Corner,
            Alignment.TopStart,
            CollageRotation.TOP_START,
            CircleShape,
            palette.onSurfaceMuted,
        ),
        CollageIconSlot(
            icons[2],
            CollageSize.Corner,
            Alignment.BottomEnd,
            CollageRotation.BOTTOM_END,
            CircleShape,
            palette.secondary,
        ),
        CollageIconSlot(
            icons.last(),
            CollageSize.SmallCorner,
            Alignment.TopEnd,
            CollageRotation.TOP_END,
            smooth20,
            palette.tertiary,
        ),
    )

private data class CollageIconSlot(
    val icon: Painter,
    val size: Dp,
    val alignment: Alignment,
    val rotation: Float,
    val shape: Shape,
    val tint: Color,
)

// named instead of inlined so detekt's MagicNumber rule doesn't flag them, and so each slot's
// silhouette is described by what it is rather than a bare float/dp value
private object CollageSize {
    val Center = 96.dp
    val Corner = 52.dp
    val SmallCorner = 44.dp
    val Height = 200.dp
    const val EDGE_INSET_FRACTION = 0.12f
    const val GLYPH_SCALE = 0.5f
}

private object CollageRotation {
    const val CENTER = -12f
    const val TOP_START = 14f
    const val BOTTOM_END = 6f
    const val TOP_END = -18f
}
