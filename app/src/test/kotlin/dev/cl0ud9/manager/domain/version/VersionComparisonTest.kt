package dev.cl0ud9.manager.domain.version

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparisonTest {
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
        assertTrue(isNewerVersion(latest = "beta", installed = "beta2"))
        assertFalse(isNewerVersion(latest = "beta", installed = "beta"))
    }

    // the real bug this covers: MicroG RE's own in-app "hide icon" toggle installs a beta build
    // outside the manager entirely (confirmed live: versionName ends up "7.2.1-dev.2"). A naive
    // string != comparison against the catalog's "7.1.1" flagged that as "update available" - and
    // tapping Update would have downgraded a newer beta back to an older stable release
    @Test
    fun `a segment with a non numeric suffix still compares by its leading digits, not by being dropped`() {
        assertFalse(isNewerVersion(latest = "7.1.1", installed = "7.2.1-dev.2"))
        assertTrue(isNewerVersion(latest = "7.3.0", installed = "7.2.1-dev.2"))
    }

    @Test
    fun `a segment with no leading digits at all falls back to zero for that segment, not a shift`() {
        // "dev" contributes no digits, but "7" and "2" elsewhere still make this comparable
        // numerically rather than falling all the way back to plain inequality
        assertTrue(isNewerVersion(latest = "7.2.0", installed = "7.dev.0"))
    }
}
