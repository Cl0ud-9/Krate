package dev.cl0ud9.krate

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SKIP_SETUP_EXTRA = "is_benchmark"
private const val WAIT_MS = 20_000L

// Krate's main screens, and who it takes instructions from; "Appearance" is on the Settings page only
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class KrateSmokeTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun outsideIntent(): Intent = Intent(context, KrateActivity::class.java).putExtra(SKIP_SETUP_EXTRA, true)

    private fun ownIntent(): Intent = internalEntryIntent(context).putExtra(SKIP_SETUP_EXTRA, true)

    private fun launch(intent: Intent) = ActivityScenario.launch<KrateActivity>(intent)

    // started the way a notification tap starts it, in a task of its own
    private fun launchOwn(intent: Intent) =
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))

    private fun waitForTabs() = compose.waitUntilAtLeastOneExists(hasContentDescription("Updates"), WAIT_MS)

    private fun showing(text: String): Boolean = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun opensOnItsTabs() {
        launch(outsideIntent()).use {
            waitForTabs()
            assertTrue(compose.onAllNodes(hasContentDescription("Home")).fetchSemanticsNodes().isNotEmpty())
            assertTrue(compose.onAllNodes(hasContentDescription("Apps")).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun movesBetweenTabsAndSettings() {
        launch(outsideIntent()).use {
            waitForTabs()
            compose.onAllNodes(hasContentDescription("Apps")).onFirst().performClick()
            compose.waitUntilAtLeastOneExists(hasText("in the Krate", substring = true), WAIT_MS)
            compose.onAllNodes(hasContentDescription("Updates")).onFirst().performClick()
            compose.waitForIdle()
            compose.onAllNodes(hasContentDescription("Home")).onFirst().performClick()
            compose.waitUntilAtLeastOneExists(hasContentDescription("Settings"), WAIT_MS)
            compose.onAllNodes(hasContentDescription("Settings")).onFirst().performClick()
            compose.waitUntilAtLeastOneExists(hasText("Appearance"), WAIT_MS)
        }
    }

    @Test
    fun anotherAppCantOpenAPage() {
        launch(outsideIntent().putExtra(EXTRA_TARGET_ROUTE, "settings")).use {
            waitForTabs()
            compose.waitForIdle()
            assertFalse(showing("Appearance"))
        }
    }

    @Test
    fun anotherAppCantCrashItWithANonsenseRoute() {
        launch(outsideIntent().putExtra(EXTRA_TARGET_ROUTE, "nonsense/route")).use {
            waitForTabs()
        }
    }

    @Test
    fun krateItselfCanOpenAPage() {
        launchOwn(ownIntent().putExtra(EXTRA_TARGET_ROUTE, "settings"))
        compose.waitUntilAtLeastOneExists(hasText("Appearance"), WAIT_MS)
    }

    @Test
    fun aRouteThisVersionDoesntHaveLeavesYouWhereYouAre() {
        launchOwn(ownIntent().putExtra(EXTRA_TARGET_ROUTE, "nonsense/route"))
        waitForTabs()
    }
}
