package dev.cl0ud9.krate.platform.autoupdate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUpdatePauseTest {
    private val now = 1_000_000_000_000L

    @Test
    fun `a pause ends a day or a week from now`() {
        assertEquals(now + 24L * 60 * 60 * 1000, pausedUntil(now, PauseLength.DAY))
        assertEquals(now + 7L * 24 * 60 * 60 * 1000, pausedUntil(now, PauseLength.WEEK))
    }

    @Test
    fun `a pause holds only until its moment`() {
        val until = pausedUntil(now, PauseLength.DAY)

        assertTrue(isPausedAt(until, now))
        assertTrue(isPausedAt(until, until - 1))
        assertFalse(isPausedAt(until, until))
        assertFalse(isPausedAt(until, until + 1))
    }

    @Test
    fun `a pause until turned back on never runs out by itself`() {
        val until = pausedUntil(now, PauseLength.UNTIL_RESUMED)
        val inTenYears = now + 10 * 365L * 24 * 60 * 60 * 1000

        assertTrue(isOpenEnded(until))
        assertTrue(isPausedAt(until, inTenYears))
        assertFalse(isOpenEnded(pausedUntil(now, PauseLength.WEEK)))
        // the screens add a little to the end time while they wait for it; that mustn't wrap round
        assertTrue(until + 60_000L > until)
    }

    @Test
    fun `no pause stored is never paused, and an old one has no hold`() {
        assertFalse(isPausedAt(0L, now))
        assertFalse(isPausedAt(now - 1, now))
    }
}
