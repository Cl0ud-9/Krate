package dev.cl0ud9.krate

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.cl0ud9.krate.ui.navigation.IconFlight
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SKIP_SETUP_EXTRA = "is_benchmark"
private const val WAIT_MS = 20_000L
private const val APP = "App Cache Cleaner"

// how far into the page's arrival the finger lands, and how long it stays down
private const val FRAMES_BEFORE_TOUCH = 8
private const val SWIPE_MS = 150L
private const val FRAMES_AFTER = 60
private const val SWIPE_FROM = 0.4f
private const val SWIPE_TO = 0.6f

// an app's icon flies from its row into its page once. A finger on the page while it's still arriving ends the flight,
// and letting go must not start it again: a swipe down at the top doesn't scroll, so the page used to look untouched
// once the finger lifted, and the icon dropped back to the list row and flew up a second time
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppPageIconTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    // opens the app's page from the Apps list, touches it mid-arrival if asked, and says how many flights went in
    private fun flightsIn(touch: (TouchInjectionScope.() -> Unit)?): Int {
        var flights = 0
        ActivityScenario
            .launch<KrateActivity>(Intent(context, KrateActivity::class.java).putExtra(SKIP_SETUP_EXTRA, true))
            .use {
                compose.waitUntilAtLeastOneExists(hasContentDescription("Apps"), WAIT_MS)
                compose.onAllNodes(hasContentDescription("Apps")).onFirst().performClick()
                compose.waitUntilAtLeastOneExists(hasText(APP), WAIT_MS)
                compose.waitForIdle()
                compose.runOnIdle { IconFlight.arrivals = 0 }
                compose.mainClock.autoAdvance = false
                compose.onAllNodes(hasText(APP)).onFirst().performClick()
                repeat(FRAMES_BEFORE_TOUCH) { compose.mainClock.advanceTimeByFrame() }
                if (touch != null) compose.onRoot().performTouchInput(touch)
                repeat(FRAMES_AFTER) { compose.mainClock.advanceTimeByFrame() }
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()
                compose.runOnIdle { flights = IconFlight.arrivals }
            }
        return flights
    }

    @Test
    fun theIconFliesInOnce() {
        assertEquals("icon flights into the page", 1, flightsIn(touch = null))
    }

    @Test
    fun aSwipeDownWhileThePageArrivesDoesNotFlyTheIconAgain() {
        val flights =
            flightsIn {
                swipeDown(startY = height * SWIPE_FROM, endY = height * SWIPE_TO, durationMillis = SWIPE_MS)
            }
        assertEquals("icon flights into the page after a swipe down", 1, flights)
    }

    @Test
    fun aScrollWhileThePageArrivesDoesNotFlyTheIconAgain() {
        val flights =
            flightsIn {
                swipeUp(startY = height * SWIPE_TO, endY = height * SWIPE_FROM, durationMillis = SWIPE_MS)
            }
        assertEquals("icon flights into the page after a scroll", 1, flights)
    }
}
