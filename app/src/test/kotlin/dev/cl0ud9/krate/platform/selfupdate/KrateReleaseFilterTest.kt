package dev.cl0ud9.krate.platform.selfupdate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KrateReleaseFilterTest {
    @Test
    fun `a finished release counts`() {
        assertTrue(isKrateRelease("v0.6.6", draft = false, prerelease = false))
    }

    @Test
    fun `the catalog's own release is not a Krate release`() {
        assertFalse(isKrateRelease("manifest-latest", draft = false, prerelease = false))
    }

    @Test
    fun `a draft is never offered`() {
        assertFalse(isKrateRelease("v0.7.0", draft = true, prerelease = false))
    }

    @Test
    fun `a test build is never offered`() {
        assertFalse(isKrateRelease("v0.7.0-beta1", draft = false, prerelease = true))
    }
}
