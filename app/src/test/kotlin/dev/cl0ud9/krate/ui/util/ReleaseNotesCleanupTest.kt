package dev.cl0ud9.krate.ui.util

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

    // shaped like LastWave's notes: changes, a rule, then a table and a warning about which file to download
    private val packages =
        """
        ### What's New in v4.2.3
        • Discord RPC: rich presence with live track status.

        ---

        ### 📦 Download Packages & Compatibility

        | Package | Recommended OS | Description |
        | :--- | :--- | :--- |
        | **`LastWave-v4.2.3-universal.apk`** (~22 MB) | **Android 10+** | **Standard Recommended Release.** |
        | **`LastWave-v4.2.3-android7.apk`** (~17 MB) | **Android 7.0 – 9.0** | **Legacy variant.** |

        > [!WARNING]
        > - For Android 10 or newer, install **`LastWave-v4.2.3-universal.apk`**.
        """.trimIndent()

    @Test
    fun `a table of apk files goes, with the heading it leaves empty and the rule`() {
        val cleaned = cleanReleaseNotes(packages)
        assertFalse(cleaned.contains("|"))
        assertFalse(cleaned.contains("Download Packages"))
        assertFalse(cleaned.contains("---"))
        assertTrue(cleaned.contains("Discord RPC"))
    }

    @Test
    fun `other tables become bullets`() {
        val notes = "| Setting | What it does |\n|---|---|\n| Crossfade | Blends tracks |\n| Gapless | No pause |"
        assertEquals("- Crossfade: Blends tracks\n- Gapless: No pause", cleanReleaseNotes(notes))
    }
}
