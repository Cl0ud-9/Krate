package dev.cl0ud9.krate.platform.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AwayTimerTest {
    private val timer = AwayTimer(graceMs = 1_500)

    @Test
    fun `something passing in front for a moment is not the person leaving`() {
        assertFalse(timer.away(true, now = 1_000, front = "com.phone.bubble"))
        assertFalse(timer.away(true, now = 2_000, front = "com.phone.bubble"))
        assertFalse(timer.away(false, now = 2_200, front = null))
    }

    @Test
    fun `the same thing staying in front for the whole grace period is`() {
        assertFalse(timer.away(true, now = 1_000, front = "com.other.app"))
        assertTrue(timer.away(true, now = 2_500, front = "com.other.app"))
        assertEquals("com.other.app", timer.who)
    }

    @Test
    fun `coming back starts the count over`() {
        timer.away(true, now = 1_000, front = "com.other.app")
        timer.away(false, now = 1_500, front = null)

        assertFalse(timer.away(true, now = 2_000, front = "com.other.app"))
        assertFalse(timer.away(true, now = 3_400, front = "com.other.app"))
        assertTrue(timer.away(true, now = 3_500, front = "com.other.app"))
    }

    @Test
    fun `nothing is named until something has been away`() {
        assertNull(timer.who)
        timer.away(false, now = 100, front = "com.other.app")
        assertNull(timer.who)
    }

    @Test
    fun `a reset forgets who it was`() {
        timer.away(true, now = 1_000, front = "com.other.app")
        timer.reset()

        assertNull(timer.who)
        assertFalse(timer.away(true, now = 1_100, front = "com.next.app"))
        assertEquals("com.next.app", timer.who)
    }
}
