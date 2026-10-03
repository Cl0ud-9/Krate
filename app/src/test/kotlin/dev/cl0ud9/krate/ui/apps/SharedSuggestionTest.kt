package dev.cl0ud9.krate.ui.apps

import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.ArtifactInfo
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.model.SupportStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedSuggestionTest {
    @Test
    fun `a GitHub link from a browser share is cut to the repository and named after it`() {
        val shared =
            parseSharedSuggestion(
                "GitHub - mihonapp/mihon: Free manga reader https://github.com/mihonapp/mihon/releases/tag/v0.20.4",
            )

        assertEquals(
            SharedSuggestion("mihon", "https://github.com/mihonapp/mihon", githubRepo = "mihonapp/mihon"),
            shared,
        )
    }

    @Test
    fun `dashes and underscores in a repository name become spaces`() {
        assertEquals("LTECleaner FOSS", parseSharedSuggestion("https://github.com/MDP43140/LTECleaner_FOSS")?.name)
        assertEquals(
            "smooth corner rect",
            parseSharedSuggestion("https://github.com/racra/smooth-corner-rect.git")?.name,
        )
    }

    @Test
    fun `any other link is kept as it is, with no name guessed`() {
        assertEquals(
            SharedSuggestion("", "https://f-droid.org/packages/org.example"),
            parseSharedSuggestion("https://f-droid.org/packages/org.example."),
        )
    }

    @Test
    fun `text without a link becomes the name or the reason`() {
        assertEquals(SharedSuggestion("Mihon", ""), parseSharedSuggestion("Mihon"))
        val long = "A manga reader with extensions, trackers and offline downloads that I use every day"
        assertEquals(SharedSuggestion("", "", long), parseSharedSuggestion(long))
        assertNull(parseSharedSuggestion("   "))
    }

    @Test
    fun `an app already in the Krate is found by its repository, whatever the case`() {
        val mihon =
            AppProfile(
                id = "mihon",
                displayName = "Mihon",
                packageName = "app.mihon",
                supportStatus = SupportStatus.SUPPORTED,
                installationMode = InstallationMode.UPDATE,
                dependencyIds = emptyList(),
                releaseNotes = null,
                enabled = true,
                artifacts =
                    listOf(
                        ArtifactInfo(
                            versionName = "0.20.4",
                            downloadUrl = "https://github.com/mihonapp/mihon/releases/download/v0.20.4/mihon.apk",
                            sha256 = "",
                            certificateSha256 = "",
                        ),
                    ),
            )

        assertEquals("mihon", listOf(mihon).publishedFrom("MihonApp/Mihon")?.id)
        assertNull(listOf(mihon).publishedFrom("mihonapp/mihon-preview"))
    }
}
