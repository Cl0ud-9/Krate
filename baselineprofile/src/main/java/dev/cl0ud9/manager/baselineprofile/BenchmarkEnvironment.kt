package dev.cl0ud9.manager.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val TARGET_APP_ID = "dev.cl0ud9.manager"

// set only from BaselineProfileGenerator's own startActivityAndWait calls, so AppRoot can skip
// onboarding and land directly on the real navigation this profile is meant to exercise
internal const val BENCHMARK_EXTRA = "is_benchmark"

private const val VISIBLE_TIMEOUT_MS = 10_000L

internal fun benchmarkTargetPackageName(): String =
    InstrumentationRegistry.getArguments().getString("targetAppId") ?: TARGET_APP_ID

internal fun MacrobenchmarkScope.waitForTargetPackageVisible(
    packageName: String = benchmarkTargetPackageName(),
    timeoutMs: Long = VISIBLE_TIMEOUT_MS,
) {
    check(device.wait(Until.hasObject(By.pkg(packageName)), timeoutMs)) {
        "Timed out waiting for $packageName to render a UI hierarchy"
    }
}
