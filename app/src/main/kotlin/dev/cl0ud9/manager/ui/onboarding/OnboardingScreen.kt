package dev.cl0ud9.manager.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import dev.cl0ud9.manager.domain.model.ThemeMode
import dev.cl0ud9.manager.ui.navigation.SideInsets
import dev.cl0ud9.manager.ui.settings.CornerRadiusEditor
import dev.cl0ud9.manager.ui.settings.SettingsViewModel
import dev.cl0ud9.manager.ui.settings.rememberSettingsViewModel
import dev.cl0ud9.manager.ui.util.RefreshOnResume
import dev.cl0ud9.manager.ui.util.managerViewModel

private const val PAGE_MS = 350
private const val OUTGOING_SHIFT = 3

private enum class OnboardingStep { WELCOME, INSTALL, NOTIFICATIONS, PLAY_PROTECT, THEME, NAVIGATION, UPDATES, FINISH }

// the notification step only exists where the runtime permission does (Android 13+)
private fun stepsForDevice(): List<OnboardingStep> =
    OnboardingStep.entries.filter {
        it != OnboardingStep.NOTIFICATIONS ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

private fun notificationsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

// first-run setup before the catalog is reachable: the permissions Krate needs, a heads-up about Play
// Protect, and a few choices about how it looks - installing from Krate is the only step that can't be skipped
@Composable
fun OnboardingScreen(
    markHidden: Boolean = false,
    onComplete: (markBounds: Rect?) -> Unit,
) {
    val context = LocalContext.current
    val onboarding = managerViewModel { container -> OnboardingViewModel(container.settingsRepository, context) }
    val settings = rememberSettingsViewModel()
    val steps = remember { stepsForDevice() }
    val stepIndex = onboarding.stepIndex.coerceIn(0, steps.lastIndex)
    val showRadiusEditor = onboarding.showRadiusEditor
    var markBounds by remember { mutableStateOf<Rect?>(null) }
    val handoff = remember(markHidden) { MarkHandoff(hidden = markHidden, onPlaced = { markBounds = it }) }
    LaunchedEffect(Unit) { onboarding.applyDarkOnce { settings.setThemeMode(ThemeMode.DARK) } }
    val permissions = rememberPermissionState(context)
    val step = steps[stepIndex]
    val canContinue = step != OnboardingStep.INSTALL || permissions.canInstall
    BackHandler(enabled = stepIndex > 0 && !showRadiusEditor) { onboarding.goToStep(stepIndex - 1) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = stepIndex,
                // pages keep clear of the side insets (landscape camera cutout); the sheet below spans the width
                modifier = Modifier.weight(1f).statusBarsPadding().windowInsetsPadding(SideInsets),
                transitionSpec = {
                    val forward = targetState > initialState
                    // held a few frames so the new page is built before it moves, instead of jumping on its first frame
                    val page = tween<IntOffset>(PAGE_MS, delayMillis = STEP_CHANGE_DELAY_MS)
                    val fade = tween<Float>(PAGE_MS, delayMillis = STEP_CHANGE_DELAY_MS)
                    (slideInHorizontally(page) { if (forward) it else -it } + fadeIn(fade))
                        .togetherWith(
                            slideOutHorizontally(page) {
                                if (forward) -it / OUTGOING_SHIFT else it / OUTGOING_SHIFT
                            } +
                                fadeOut(fade),
                        )
                },
                label = "onboarding-page",
            ) { index ->
                StepPage(
                    step = steps[index],
                    settings = settings,
                    permissions = permissions,
                    onCustomizeRadius = { onboarding.showRadiusEditor = true },
                    handoff = handoff,
                )
            }
            OnboardingBottomBar(step = stepIndex, lastStep = steps.lastIndex, canContinue = canContinue) {
                if (stepIndex < steps.lastIndex) {
                    onboarding.goToStep(stepIndex + 1)
                } else {
                    onboarding.completeOnboarding()
                    onComplete(markBounds)
                }
            }
        }
        RadiusEditorOverlay(visible = showRadiusEditor, onClose = { onboarding.showRadiusEditor = false })
    }
}

// the corner radius editor, sliding up over setup
@Composable
private fun RadiusEditorOverlay(
    visible: Boolean,
    onClose: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        BackHandler(onBack = onClose)
        CornerRadiusEditor(onClose = onClose)
    }
}

// what setup needs to know about permissions, kept current as the user grants them
private class PermissionState(
    val canInstall: Boolean,
    val notificationsAllowed: Boolean,
    val openInstallSettings: () -> Unit,
    val requestNotifications: () -> Unit,
)

@Composable
private fun rememberPermissionState(context: Context): PermissionState {
    var canInstall by remember { mutableStateOf(context.packageManager.canRequestPackageInstalls()) }
    var notifications by remember { mutableStateOf(notificationsAllowed(context)) }
    // both can change outside this screen (Settings, or the permission dialog), so re-read on every return
    RefreshOnResume {
        canInstall = context.packageManager.canRequestPackageInstalls()
        notifications = notificationsAllowed(context)
    }
    val installLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            canInstall = context.packageManager.canRequestPackageInstalls()
        }
    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notifications = granted || notificationsAllowed(context)
        }
    return PermissionState(
        canInstall = canInstall,
        notificationsAllowed = notifications,
        openInstallSettings = {
            installLauncher.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            )
        },
        requestNotifications = {
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
    )
}

@Composable
private fun StepPage(
    step: OnboardingStep,
    settings: SettingsViewModel,
    permissions: PermissionState,
    onCustomizeRadius: () -> Unit,
    handoff: MarkHandoff,
) {
    when (step) {
        OnboardingStep.WELCOME -> WelcomePage()
        OnboardingStep.INSTALL ->
            StepPageLayout(
                title = "Allow installs from Krate",
                description =
                    "Krate installs and updates apps directly, like an app store would. Android asks for this once.",
                icons = INSTALL_ICONS,
                action =
                    PageAction(
                        "Open settings",
                        "Permission granted",
                        permissions.canInstall,
                        permissions.openInstallSettings,
                    ),
            )
        OnboardingStep.NOTIFICATIONS ->
            StepPageLayout(
                title = "Notifications",
                description =
                    "Hear about new versions of your apps, and when downloads finish, even with Krate closed.",
                icons = NOTIFICATION_ICONS,
                action =
                    PageAction(
                        "Allow notifications",
                        "Notifications allowed",
                        permissions.notificationsAllowed,
                        permissions.requestNotifications,
                    ),
            )
        OnboardingStep.PLAY_PROTECT ->
            StepPageLayout(
                title = "About Play Protect",
                description =
                    "Some apps in Krate are signed by this project instead of Google. " +
                        "Play Protect may ask to scan them " +
                        "when they're installed. That's expected, not a sign of a broken app.",
                icons = PLAY_PROTECT_ICONS,
            )
        OnboardingStep.THEME -> ThemePage(viewModel = settings)
        OnboardingStep.NAVIGATION -> NavigationPage(viewModel = settings, onCustomizeRadius = onCustomizeRadius)
        OnboardingStep.UPDATES -> UpdatesPage(viewModel = settings)
        OnboardingStep.FINISH -> FinishPage(handoff = handoff)
    }
}
