package dev.cl0ud9.manager.platform.selfupdate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagerUpdateCheckerTest {
    @Test
    fun `higher patch version is newer`() {
        assertTrue(isNewerVersion(latest = "0.1.1", installed = "0.1.0"))
    }

    @Test
    fun `same version is not newer`() {
        assertFalse(isNewerVersion(latest = "0.1.0", installed = "0.1.0"))
    }

    @Test
    fun `lower version is not newer`() {
        assertFalse(isNewerVersion(latest = "0.1.0", installed = "0.1.1"))
    }

    @Test
    fun `double digit segment beats single digit segment numerically, not lexically`() {
        assertTrue(isNewerVersion(latest = "1.4.10", installed = "1.4.9"))
    }

    @Test
    fun `missing trailing segment is treated as zero`() {
        assertTrue(isNewerVersion(latest = "0.2", installed = "0.1.9"))
        assertFalse(isNewerVersion(latest = "0.1", installed = "0.1.0"))
    }

    @Test
    fun `non numeric tags fall back to plain inequality`() {
        assertTrue(isNewerVersion(latest = "beta", installed = "0.1.0"))
        assertFalse(isNewerVersion(latest = "beta", installed = "beta"))
    }
}
