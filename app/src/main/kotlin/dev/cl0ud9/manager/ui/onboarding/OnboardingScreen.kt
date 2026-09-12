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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.ui.theme.ShapeCache
import dev.cl0ud9.manager.ui.util.RefreshOnResume
import dev.cl0ud9.manager.ui.util.managerViewModel

private enum class OnboardingStep {
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
            CornerPercents(
                LEAF_MAJOR_CORNER_PERCENT,
                LEAF_MINOR_CORNER_PERCENT,
                LEAF_MAJOR_CORNER_PERCENT,
                LEAF_MINOR_CORNER_PERCENT,
            )
    }

// runs once before the Apps catalog is reachable, amendment 44.4 of the spec: install-unknown-apps
// grant, notification permission, and a Play Protect heads-up are anticipated here instead of being
// discovered mid-deployment
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val viewModel = managerViewModel { container -> OnboardingViewModel(container.settingsRepository) }
    val context = LocalContext.current

    val steps =
        remember {
            buildList {
                add(OnboardingStep.INSTALL_UNKNOWN_APPS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(OnboardingStep.NOTIFICATION_PERMISSION)
                add(OnboardingStep.PLAY_PROTECT_NOTICE)
            }
        }
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
    Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.Center) {
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
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
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
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
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
        OnboardingStep.INSTALL_UNKNOWN_APPS -> {
            OnboardingStepContent(
                icon = Icons.Filled.Security,
                title = "Allow installs from this app",
                body =
                    "App Manager installs and updates apps directly, the same way an app store " +
                        "would. Android requires a one-time permission for that.",
            ) {
                if (canInstallUnknownApps) {
                    StatusLine("Permission granted.")
                } else {
                    OutlinedButton(onClick = onOpenInstallSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Open settings")
                    }
                }
            }
        }

        OnboardingStep.NOTIFICATION_PERMISSION -> {
            OnboardingStepContent(
                icon = Icons.Filled.Notifications,
                title = "Stay notified about updates",
                body = "Get notified when a new version is available for one of your apps.",
            ) {
                OutlinedButton(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth()) {
                    Text("Allow notifications")
                }
            }
        }

        OnboardingStep.PLAY_PROTECT_NOTICE -> {
            OnboardingStepContent(
                icon = Icons.Filled.Shield,
                title = "About Play Protect warnings",
                body =
                    "Some apps in the catalog, like Videoapp Patched, are signed with this " +
                        "project's own key instead of Google's. Play Protect may warn when " +
                        "installing them - that's expected, not a sign of a broken build.",
            ) {}
        }
    }
}

// a bigger hero-style icon badge instead of SectionHeader's compact 30dp chip - this screen only
// ever shows one step at a time full-screen, so it can afford (and benefits from) more presence
// than a card's inline header
@Composable
private fun OnboardingStepContent(
    icon: ImageVector,
    title: String,
    body: String,
    action: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(ShapeCache.smooth20)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action()
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
}
