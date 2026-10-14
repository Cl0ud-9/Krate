package dev.cl0ud9.krate.platform.autoupdate

import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.model.SupportStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateModeTest {
    private val on = AutoUpdateGlobal(on = true, manual = emptySet())

    @Test
    fun `an installed app Krate owns updates automatically`() {
        assertEquals(UpdateMode.AUTOMATIC, updateMode(on, app(), installed = true, krateOwns = true))
    }

    @Test
    fun `nothing is claimed while automatic updates are off`() {
        val off = AutoUpdateGlobal(on = false, manual = emptySet())

        assertNull(updateMode(off, app(), installed = true, krateOwns = true))
    }

    @Test
    fun `nothing is claimed for an app that isn't on the phone`() {
        assertNull(updateMode(on, app(), installed = false, krateOwns = true))
    }

    @Test
    fun `an app kept on by hand says so`() {
        val manual = on.copy(manual = setOf("reader"))

        assertEquals(UpdateMode.BY_HAND, updateMode(manual, app(), installed = true, krateOwns = true))
    }

    @Test
    fun `an app another store installed is by hand until Krate has installed it once`() {
        assertEquals(UpdateMode.BY_HAND, updateMode(on, app(), installed = true, krateOwns = false))
    }

    @Test
    fun `an app that reinstalls from scratch is always by hand`() {
        val clean = app(InstallationMode.CLEAN_INSTALL)

        assertEquals(UpdateMode.BY_HAND, updateMode(on, clean, installed = true, krateOwns = true))
    }

    @Test
    fun `another app's choice doesn't change this one`() {
        val others = on.copy(manual = setOf("other"))

        assertEquals(UpdateMode.AUTOMATIC, updateMode(others, app(), installed = true, krateOwns = true))
    }

    private fun app(mode: InstallationMode = InstallationMode.UPDATE) =
        AppProfile(
            id = "reader",
            displayName = "Reader",
            packageName = "com.example.reader",
            supportStatus = SupportStatus.SUPPORTED,
            installationMode = mode,
            dependencyIds = emptyList(),
            releaseNotes = null,
            enabled = true,
            artifacts = emptyList(),
        )
}
