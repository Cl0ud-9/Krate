package dev.cl0ud9.manager.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val MAX_ITERATIONS = 5
private const val STABLE_ITERATIONS = 3
private const val POST_LAUNCH_DELAY_MS = 1_000L

// exercises the navigation this app actually spends most of its time in - tab switching, opening
// Settings/Appearance, opening an App Details page - so ART has an ahead-of-time compiled path for
// all of it from the very first cold start after install, instead of interpreting/JIT-warming those
// same code paths the slow way on a user's first few taps
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateStartupProfile() {
        rule.collect(
            packageName = benchmarkTargetPackageName(),
            includeInStartupProfile = true,
            maxIterations = MAX_ITERATIONS,
            stableIterations = STABLE_ITERATIONS,
        ) {
            startBenchmarkActivity()
        }
    }

    @Test
    fun generateBaselineProfile() {
        rule.collect(
            packageName = benchmarkTargetPackageName(),
            includeInStartupProfile = false,
            maxIterations = MAX_ITERATIONS,
            stableIterations = STABLE_ITERATIONS,
        ) {
            startBenchmarkActivity()
            runHomeFlow()
            runAppsAndDetailsFlow()
            runUpdatesFlow()
            runSettingsFlow()
        }
    }

    private fun MacrobenchmarkScope.startBenchmarkActivity() {
        pressHome()
        startActivityAndWait { intent -> intent.putExtra(BENCHMARK_EXTRA, true) }
        waitForTargetPackageVisible()
        waitForUi(POST_LAUNCH_DELAY_MS)
    }

    private fun MacrobenchmarkScope.runHomeFlow() {
        clickDescription("Home")
        scrollDownAndUp()
    }

    private fun MacrobenchmarkScope.runAppsAndDetailsFlow() {
        clickDescription("Apps")
        scrollDownAndUp()
        openFirstListItem()
        waitForUi(POST_LAUNCH_DELAY_MS)
        scrollDownAndUp()
        device.pressBack()
        waitForUi()
    }

    private fun MacrobenchmarkScope.runUpdatesFlow() {
        clickDescription("Updates")
        scrollDownAndUp()
    }

    private fun MacrobenchmarkScope.runSettingsFlow() {
        clickDescription("Home")
        clickDescription("Settings")
        waitForUi(POST_LAUNCH_DELAY_MS)
        scrollDownAndUp()
        clickText("Appearance")
        waitForUi(POST_LAUNCH_DELAY_MS)
        scrollDownAndUp()
        // extra settle time before each Back click - right after a scroll, the collapsing header
        // can still be mid-animation, which is exactly when clickWhenFound's retry used to be needed
        waitForUi(POST_LAUNCH_DELAY_MS)
        clickDescription("Back")
        waitForUi(POST_LAUNCH_DELAY_MS)
        clickDescription("Back")
        waitForUi()
    }
}
