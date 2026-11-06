package dev.cl0ud9.krate.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val ITERATIONS = 8
private const val STARTUP_ITERATIONS = 10
internal const val SETTLE_MS = 1_000L

// how smooth Krate is where people feel it: starting up, switching tabs, scrolling, and swiping back from a page,
// with liquid glass off and on. tools/benchmark/compare.py compares a run with the last release's numbers
@RunWith(AndroidJUnit4::class)
@LargeTest
class SmoothnessBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private val app = benchmarkTargetPackageName()

    @Test
    fun coldStart() =
        rule.measureRepeated(
            packageName = app,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.COLD,
            iterations = STARTUP_ITERATIONS,
            // Krate's background work can bring it back up between runs, which would make the start a warm one
            setupBlock = {
                pressHome()
                killProcess()
            },
        ) {
            pressHome()
            startActivityAndWait { it.putExtra(BENCHMARK_EXTRA, true) }
        }

    @Test
    fun switchTabs() =
        measureFrames(glass = false) {
            repeat(2) {
                clickDescription("Apps")
                clickDescription("Updates")
                clickDescription("Home")
            }
        }

    @Test
    fun scrollAppsList() =
        measureFrames(glass = false, setup = { clickDescription("Apps") }) {
            scrollDownAndUp()
        }

    @Test
    fun swipeBackFromSettings() = measureFrames(glass = false) { openAndSwipeBack { openSettings() } }

    @Test
    fun swipeBackFromSettingsWithGlass() = measureFrames(glass = true) { openAndSwipeBack { openSettings() } }

    @Test
    fun swipeBackFromAnAppPage() =
        measureFrames(glass = false, setup = { clickDescription("Apps") }) {
            openAndSwipeBack { openFirstListItem() }
        }

    @Test
    fun swipeBackFromAnAppPageWithGlass() =
        measureFrames(glass = true, setup = { clickDescription("Apps") }) {
            openAndSwipeBack { openFirstListItem() }
        }

    private fun measureFrames(
        glass: Boolean,
        setup: MacrobenchmarkScope.() -> Unit = {},
        measure: MacrobenchmarkScope.() -> Unit,
    ) = rule.measureRepeated(
        packageName = app,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait { it.putExtra(BENCHMARK_EXTRA, true) }
            waitForTargetPackageVisible()
            setGlass(glass)
            setup()
            waitForUi(SETTLE_MS)
        },
    ) { measure() }
}
