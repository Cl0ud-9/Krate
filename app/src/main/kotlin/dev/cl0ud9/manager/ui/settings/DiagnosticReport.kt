package dev.cl0ud9.manager.ui.settings

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.cl0ud9.manager.domain.model.ActivityEntry
import dev.cl0ud9.manager.ui.util.formatRelativeTime

private const val RECENT_ACTIVITY_LIMIT = 5

// device/package facts read via Context, gathered here rather than in SettingsViewModel - matching
// how rememberVersionName() already keeps this kind of PackageManager lookup in the UI layer instead
// of smuggling a Context into a ViewModel. Android has no public API for a normal (non-system) app to
// read its own logcat output, so "attach logs" here means this self-assembled diagnostic summary -
// device/app/catalog facts plus recent activity - not a raw system log
@Composable
internal fun rememberDeviceSummary(): String {
    val context = LocalContext.current
    return remember {
        val versionName =
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                .getOrNull() ?: "unknown"
        buildString {
            appendLine("App: ${context.packageName} $versionName")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        }
    }
}

internal fun formatDiagnosticReport(
    deviceSummary: String,
    catalogCount: Int,
    installedCount: Int,
    recentActivity: List<ActivityEntry>,
): String =
    buildString {
        appendLine("=== App Manager diagnostic report ===")
        appendLine()
        append(deviceSummary)
        appendLine()
        appendLine("Catalog: $catalogCount apps, $installedCount installed")
        val recent = recentActivity.take(RECENT_ACTIVITY_LIMIT)
        if (recent.isNotEmpty()) {
            appendLine()
            appendLine("Recent activity:")
            recent.forEach { entry ->
                appendLine("- ${entry.appName}: ${entry.action} (${formatRelativeTime(entry.timestampMillis)})")
            }
        }
    }
