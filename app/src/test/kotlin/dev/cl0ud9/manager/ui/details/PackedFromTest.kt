package dev.cl0ud9.manager.ui.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackedFromTest {
    @Test
    fun `a public release download names its repo`() {
        val url = "https://github.com/mihonapp/mihon/releases/download/v0.20.4/mihon-v0.20.4.apk"
        assertEquals(PackedFrom.Repo("mihonapp/mihon"), packedFrom(url, requiresAuth = false))
    }

    @Test
    fun `a download behind a token is one of Krate's own builds`() {
        val url = "https://api.github.com/repos/someone/artifacts/releases/assets/123"
        assertEquals(PackedFrom.KrateBuilds, packedFrom(url, requiresAuth = true))
    }

    @Test
    fun `an unrecognised or missing link says nothing rather than guess`() {
        assertNull(packedFrom("https://example.com/app.apk", requiresAuth = false))
        assertNull(packedFrom(null, requiresAuth = false))
    }
}
