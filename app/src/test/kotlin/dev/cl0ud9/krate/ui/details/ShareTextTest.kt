package dev.cl0ud9.krate.ui.details

import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.model.SupportStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareTextTest {
    private fun app(description: String?) =
        AppProfile(
            id = "calc",
            displayName = "Calculator",
            packageName = "org.fossify.math",
            supportStatus = SupportStatus.SUPPORTED,
            installationMode = InstallationMode.UPDATE,
            dependencyIds = emptyList(),
            releaseNotes = null,
            enabled = true,
            artifacts = emptyList(),
            description = description,
        )

    @Test
    fun `a friend gets the name, what it's for, where to get it and where to get Krate`() {
        assertEquals(
            "Thought you'd like Calculator. Sums with no ads.\n\n" +
                "Get it here: https://github.com/FossifyOrg/Calculator\n\n" +
                "I keep it up to date with Krate: https://github.com/Cl0ud-9/Krate",
            shareText(app("Sums with no ads"), "https://github.com/FossifyOrg/Calculator"),
        )
    }

    @Test
    fun `no description, no stray words`() {
        assertEquals(
            "Thought you'd like Calculator.\n\nGet it here: https://x\n\nI keep it up to date with Krate: " +
                "https://github.com/Cl0ud-9/Krate",
            shareText(app(null), "https://x"),
        )
    }
}
