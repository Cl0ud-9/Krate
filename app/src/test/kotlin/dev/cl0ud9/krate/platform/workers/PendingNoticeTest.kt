package dev.cl0ud9.krate.platform.workers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingNoticeTest {
    private fun post(
        same: Boolean,
        wasReady: Boolean = false,
        downloaded: Boolean = false,
        showing: Boolean = false,
        covered: Boolean = false,
    ) = shouldPostPendingNotice(same, wasReady, downloaded, showing, covered)

    @Test
    fun `a new set of updates is announced`() {
        assertTrue(post(same = false))
        assertTrue(post(same = false, showing = true))
    }

    @Test
    fun `the same updates stay quiet`() {
        assertFalse(post(same = true))
        assertFalse(post(same = true, wasReady = true, downloaded = true, showing = true))
    }

    @Test
    fun `a download finishing corrects the notice while it is still there`() {
        assertTrue(post(same = true, downloaded = true, showing = true))
    }

    @Test
    fun `a download finishing never raises a notice that was tapped or swiped away`() {
        assertFalse(post(same = true, downloaded = true, showing = false))
    }

    @Test
    fun `a file that has gone is corrected in place and not raised again`() {
        assertTrue(post(same = true, wasReady = true, downloaded = false, showing = true))
        assertFalse(post(same = true, wasReady = true, downloaded = false, showing = false))
    }

    @Test
    fun `nothing is added when the needs you notice already names these apps`() {
        assertFalse(post(same = false, covered = true))
        assertFalse(post(same = true, downloaded = true, showing = true, covered = true))
    }
}
