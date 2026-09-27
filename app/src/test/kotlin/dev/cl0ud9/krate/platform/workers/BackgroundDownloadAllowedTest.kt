package dev.cl0ud9.krate.platform.workers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundDownloadAllowedTest {
    @Test
    fun `wi-fi always downloads, whatever the mobile data choice`() {
        assertTrue(allowed(metered = false, mobileDataAllowed = false))
        assertTrue(allowed(metered = false, mobileDataAllowed = true))
    }

    @Test
    fun `mobile data downloads only once the user allows it`() {
        assertFalse(allowed(metered = true, mobileDataAllowed = false))
        assertTrue(allowed(metered = true, mobileDataAllowed = true))
    }

    @Test
    fun `mobile data never downloads while roaming or under Data Saver`() {
        assertFalse(allowed(metered = true, mobileDataAllowed = true, roaming = true))
        assertFalse(allowed(metered = true, mobileDataAllowed = true, dataSaver = true))
    }

    private fun allowed(
        metered: Boolean,
        mobileDataAllowed: Boolean,
        roaming: Boolean = false,
        dataSaver: Boolean = false,
    ) = backgroundDownloadAllowed(metered, roaming, dataSaver, mobileDataAllowed)
}
