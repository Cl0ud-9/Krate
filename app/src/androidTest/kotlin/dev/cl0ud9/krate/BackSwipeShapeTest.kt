package dev.cl0ud9.krate

import android.content.Context
import android.content.Intent
import androidx.activity.BackEventCompat
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SKIP_SETUP_EXTRA = "is_benchmark"
private const val WAIT_MS = 20_000L
private const val HELD_AT = 0.5f

// the back button's own animation keeps the page at 85% or more and slides it right
private const val MIN_SCALE = 0.82f
private const val MIN_SLIDE_OF_WIDTH = 0.05f

// a back swipe held halfway must look like the back button's animation: the page slides right and stays close to
// full size. Navigation 2.10 shrinks it in place to 70% unless Krate passes its own back-swipe transitions
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class BackSwipeShapeTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun launch() =
        ActivityScenario.launch<KrateActivity>(
            Intent(context, KrateActivity::class.java).putExtra(SKIP_SETUP_EXTRA, true),
        )

    private fun tap(matcher: SemanticsMatcher) = compose.onAllNodes(matcher).onFirst().performClick()

    private fun boundsOf(matcher: SemanticsMatcher): Rect =
        compose
            .onAllNodes(matcher)
            .onFirst()
            .fetchSemanticsNode()
            .boundsInRoot

    private fun openSettings() {
        compose.waitUntilAtLeastOneExists(hasContentDescription("Updates"), WAIT_MS)
        tap(hasContentDescription("Settings"))
        compose.waitUntilAtLeastOneExists(hasText("Appearance"), WAIT_MS)
        compose.waitForIdle()
    }

    private fun backEvent(progress: Float) = BackEventCompat(0f, 1000f, progress, BackEventCompat.EDGE_LEFT)

    // holds a back swipe at HELD_AT, checks the page's shape through one of its words, then lets go or calls it off
    private fun holdSwipe(
        scenario: ActivityScenario<KrateActivity>,
        marker: SemanticsMatcher,
        page: String,
    ): Rect {
        val rest = boundsOf(marker)
        var screenWidth = 0f
        scenario.onActivity { activity ->
            screenWidth =
                activity.window.decorView.width
                    .toFloat()
            activity.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(0f))
            activity.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(HELD_AT))
        }
        compose.waitForIdle()
        val held = boundsOf(marker)

        val scale = held.width / rest.width
        // the page scales about its own centre, so take that out to leave the slide
        val centre = screenWidth / 2
        val slide = held.center.x - centre - scale * (rest.center.x - centre)
        assertTrue("$page shrank to ${"%.2f".format(scale)} of its size mid-swipe", scale >= MIN_SCALE)
        assertTrue(
            "$page moved ${slide.toInt()} px of $screenWidth mid-swipe instead of sliding right",
            slide >= MIN_SLIDE_OF_WIDTH * screenWidth,
        )
        return rest
    }

    @Test
    fun settingsSlidesAwayOnABackSwipe() {
        launch().use { scenario ->
            openSettings()
            holdSwipe(scenario, hasText("Appearance"), "Settings")
        }
    }

    @Test
    fun aSettingsPageSlidesAwayOnABackSwipe() {
        launch().use { scenario ->
            openSettings()
            tap(hasText("Appearance"))
            compose.waitUntilAtLeastOneExists(hasText("Navigation bar"), WAIT_MS)
            compose.waitForIdle()
            holdSwipe(scenario, hasText("Navigation bar"), "Appearance")
        }
    }

    @Test
    fun aSwipeCalledOffKeepsThePage() {
        launch().use { scenario ->
            openSettings()
            val rest = holdSwipe(scenario, hasText("Appearance"), "Settings")
            scenario.onActivity { it.onBackPressedDispatcher.dispatchOnBackCancelled() }
            compose.waitForIdle()
            val back = boundsOf(hasText("Appearance"))
            assertTrue(
                "Settings didn't settle back after a cancelled swipe: $back, was $rest",
                kotlin.math.abs(back.left - rest.left) < 2f && kotlin.math.abs(back.width - rest.width) < 2f,
            )
        }
    }

    @Test
    fun aFinishedSwipeGoesBack() {
        launch().use { scenario ->
            openSettings()
            holdSwipe(scenario, hasText("Appearance"), "Settings")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            compose.waitUntil(WAIT_MS) { compose.onAllNodes(hasText("Appearance")).fetchSemanticsNodes().isEmpty() }
        }
    }
}
