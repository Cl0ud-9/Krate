package dev.cl0ud9.krate.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepBackTest {
    @Test
    fun `opening a page is a step forward`() {
        assertFalse(isStepBack(current = "page", previous = "apps", from = "apps", to = "page"))
    }

    @Test
    fun `leaving a page that is gone from the back stack is going back`() {
        assertTrue(isStepBack(current = "apps", previous = "home", from = "page", to = "apps"))
    }

    @Test
    fun `heading to the page underneath is going back, even before the pop shows in the back stack`() {
        // a quick back swipe on a page still arriving: Navigation called this a plain change and the page slid left
        assertTrue(isStepBack(current = "page", previous = "apps", from = "page", to = "apps"))
    }

    @Test
    fun `a page opened from a page is a step forward`() {
        assertFalse(isStepBack(current = "appearance", previous = "settings", from = "settings", to = "appearance"))
    }
}
