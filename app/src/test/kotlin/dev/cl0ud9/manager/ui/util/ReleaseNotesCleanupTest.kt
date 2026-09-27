package dev.cl0ud9.manager.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesCleanupTest {
    private val mihon =
        """
        Check out the [past release notes](https://github.com/mihonapp/mihon/releases) if you're upgrading.

        <!-->

        ### 🧩 Fixes
        - Fixed some dates being wrongly shown as "Today" (@MajorTanya)

        <!-->

        > [!TIP]
        >
        > ### If you are unsure which version to download then go with `mihon-v0.20.4.apk`
        """.trimIndent()

    @Test
    fun `empty html comments go, and the text between them stays`() {
        val cleaned = cleanReleaseNotes(mihon)
        assertFalse(cleaned.contains("<!--"))
        assertTrue(cleaned.contains("### 🧩 Fixes"))
        assertTrue(cleaned.contains("- Fixed some dates"))
    }

    @Test
    fun `an alert about which apk to download is dropped, since Krate picks the build`() {
        val cleaned = cleanReleaseNotes(mihon)
        assertFalse(cleaned.contains("[!TIP]"))
        assertFalse(cleaned.contains(".apk"))
    }

    @Test
    fun `other alerts and quotes are kept`() {
        val notes = "Intro\n\n> [!NOTE]\n> Backups from 0.19 need a manual restore\n\n> plain quote"
        assertEquals(notes, cleanReleaseNotes(notes))
    }

    @Test
    fun `full comments and common tags are stripped`() {
        val notes = "<!-- hidden\nacross lines -->Shown<br>\n<details><summary>More</summary>Inside</details>"
        assertEquals("Shown\nMoreInside", cleanReleaseNotes(notes))
    }

    @Test
    fun `the collapsed preview opens on the changes, past an intro`() {
        val (preview, skipped) = releaseNotesPreview(mihon)
        assertTrue(skipped)
        assertTrue(preview.startsWith("### 🧩 Fixes"))
    }

    @Test
    fun `notes without headings or bullets preview from the top`() {
        assertEquals("Just a line\nand another" to false, releaseNotesPreview("Just a line\nand another"))
    }

    @Test
    fun `angle brackets that aren't tags survive, as in code`() {
        val notes = "- Added comparison operators (`>`, `>=`, `<`, `<=`)"
        assertEquals(notes, cleanReleaseNotes(notes))
    }
}
