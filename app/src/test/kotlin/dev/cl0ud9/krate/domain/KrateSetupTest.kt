package dev.cl0ud9.krate.domain

import dev.cl0ud9.krate.domain.setup.KrateSetup
import dev.cl0ud9.krate.domain.setup.SETUP_VERSION
import dev.cl0ud9.krate.domain.setup.SetupReading
import dev.cl0ud9.krate.domain.setup.SetupSettings
import dev.cl0ud9.krate.domain.setup.SetupTracked
import dev.cl0ud9.krate.domain.setup.encodeSetup
import dev.cl0ud9.krate.domain.setup.readSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KrateSetupTest {
    private val sha = "a".repeat(64)

    private fun tracked(
        owner: String = "owner",
        repo: String = "repo",
        forge: String = "github",
        packageName: String = "com.example.app",
        sha256: String = sha,
    ) = SetupTracked(forge, owner, repo, packageName, "Example", sha256, "app-*.apk")

    private fun ready(text: String) = readSetup(text) as SetupReading.Ready

    @Test
    fun `a setup survives being saved and read back`() {
        val setup =
            KrateSetup(
                exportedAtMillis = 5L,
                tracked = listOf(tracked()),
                manualUpdates = listOf("mihon"),
                settings = SetupSettings(themeMode = "DARK", liquidGlass = true, navBarCornerRadius = 28),
            )

        val back = ready(encodeSetup(setup))

        assertEquals(setup, back.setup)
        assertEquals(0, back.skipped)
    }

    @Test
    fun `text that is not a setup is refused`() {
        assertEquals(SetupReading.NotASetup, readSetup(""))
        assertEquals(SetupReading.NotASetup, readSetup("hello"))
        assertEquals(SetupReading.NotASetup, readSetup("[1,2,3]"))
        assertEquals(SetupReading.NotASetup, readSetup("{\"version\": 0}"))
    }

    @Test
    fun `a file from a newer Krate is refused rather than guessed at`() {
        val text = "{\"version\": ${SETUP_VERSION + 1}}"

        assertEquals(SetupReading.FromANewerKrate(SETUP_VERSION + 1), readSetup(text))
    }

    @Test
    fun `entries that do not look right are dropped and counted`() {
        val bad =
            listOf(
                tracked(owner = "own er"),
                tracked(repo = "../etc"),
                tracked(forge = "evilforge"),
                tracked(packageName = "not a package"),
                tracked(sha256 = "xyz"),
                tracked(sha256 = "a".repeat(63)),
            )
        val setup = KrateSetup(tracked = bad + tracked(owner = "good"))

        val read = ready(encodeSetup(setup))

        assertEquals(listOf("good"), read.setup.tracked.map { it.owner })
        assertEquals(6, read.skipped)
    }

    @Test
    fun `the same repository twice is kept once, whatever the case`() {
        val setup = KrateSetup(tracked = listOf(tracked(owner = "Owner"), tracked(owner = "owner")))

        assertEquals(1, ready(encodeSetup(setup)).setup.tracked.size)
    }

    @Test
    fun `settings with a value Krate does not have are left unset`() {
        val setup =
            KrateSetup(
                settings = SetupSettings(themeMode = "PURPLE", navBarStyle = "FULL_WIDTH", navBarCornerRadius = 9999),
            )

        val settings = ready(encodeSetup(setup)).setup.settings

        assertNull(settings.themeMode)
        assertEquals("FULL_WIDTH", settings.navBarStyle)
        assertNull(settings.navBarCornerRadius)
    }

    @Test
    fun `a huge file is not read at all`() {
        assertEquals(SetupReading.NotASetup, readSetup("x".repeat(600 * 1024)))
    }

    @Test
    fun `no password, token or phone detail is part of the format`() {
        val text = encodeSetup(KrateSetup(tracked = listOf(tracked())))

        listOf("token", "password", "secret", "androidId", "serial", "imei").forEach {
            assertFalse(it, text.contains(it, ignoreCase = true))
        }
        assertTrue(text.contains("certificateSha256"))
    }
}
