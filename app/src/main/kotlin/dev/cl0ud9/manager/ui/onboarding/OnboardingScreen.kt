package dev.cl0ud9.manager.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.cl0ud9.manager.ui.components.SectionHeader
import dev.cl0ud9.manager.ui.util.RefreshOnResume
import dev.cl0ud9.manager.ui.util.managerViewModel

private enum class OnboardingStep {
    INSTALL_UNKNOWN_APPS,
    NOTIFICATION_PERMISSION,
    PLAY_PROTECT_NOTICE,
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

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
            StepDots(total = steps.size, current = stepIndex)
            StepBody(
                step = steps[stepIndex],
                canInstallUnknownApps = canInstallUnknownApps,
                onOpenInstallSettings = {
                    installSourceLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                    )
                },
                onRequestNotifications = {
                    notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS,
                    )
                },
            )
        }

        val canContinue = steps[stepIndex] != OnboardingStep.INSTALL_UNKNOWN_APPS || canInstallUnknownApps
        Button(onClick = onContinue, enabled = canContinue, modifier = Modifier.fillMaxWidth()) {
            Text(if (stepIndex == steps.lastIndex) "Get started" else "Continue")
        }
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

@Composable
private fun OnboardingStepContent(
    icon: ImageVector,
    title: String,
    body: String,
    action: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(title = title, icon = icon)
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
        action()
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
}

@Composable
private fun StepDots(
    total: Int,
    current: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { index ->
            val active = index == current
            Box(
                modifier =
                    Modifier
                        .size(if (active) 10.dp else 8.dp)
                        .background(
                            color =
                                if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                            shape = CircleShape,
                        ),
            )
        }
    }
}
