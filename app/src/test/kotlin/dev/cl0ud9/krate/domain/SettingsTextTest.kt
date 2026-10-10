package dev.cl0ud9.krate.domain

import dev.cl0ud9.krate.data.catalog.ManifestGuideDto
import dev.cl0ud9.krate.data.catalog.ManifestRecommendedDto
import dev.cl0ud9.krate.data.catalog.toDomain
import dev.cl0ud9.krate.domain.settings.SettingsText
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTextTest {
    // the shape an app's Import / Export box exports: one setting a line, outer braces left off
    private val picks =
        """
        "custom_branding_icon": "original",
        "custom_branding_name": 1,
        "check_updates": false,
        "spoof_client": "android_reel"
        """.trimIndent()

    @Test
    fun `reads text with or without its outer braces, and a blank box as no settings`() {
        assertEquals(4, SettingsText.parse(picks)!!.size)
        assertEquals(4, SettingsText.parse("{$picks}")!!.size)
        assertTrue(SettingsText.parse("   ")!!.isEmpty())
        assertNull(SettingsText.parse("not settings at all"))
    }

    @Test
    fun `the picks go over the app's own settings and leave the rest alone`() {
        val own = SettingsText.parse("\"tap_to_seek\": false,\n\"check_updates\": true")!!
        val merged = SettingsText.merge(own, SettingsText.parse(picks)!!)
        assertEquals(JsonPrimitive(false), merged["tap_to_seek"])
        assertEquals(JsonPrimitive(false), merged["check_updates"])
        assertEquals(JsonPrimitive(1), merged["custom_branding_name"])
        // the app's own order first, new ones after
        assertEquals(listOf("tap_to_seek", "check_updates"), merged.keys.take(2))
    }

    @Test
    fun `only picks that differ count as changes`() {
        val own = SettingsText.parse("\"check_updates\": false,\n\"spoof_client\": \"other\"")!!
        val changes = SettingsText.changes(own, SettingsText.parse(picks)!!)
        assertEquals(setOf("custom_branding_icon", "custom_branding_name", "spoof_client"), changes)
        assertTrue(SettingsText.changes(SettingsText.parse(picks)!!, SettingsText.parse(picks)!!).isEmpty())
    }

    @Test
    fun `writes back in the app's own shape, keeping numbers as numbers`() {
        val settings = SettingsText.parse(picks)!!
        val written = SettingsText.write(settings, braces = false)
        assertFalse(written.trim().startsWith("{"))
        assertTrue(written.contains("\"custom_branding_name\": 1,"))
        assertEquals(settings, SettingsText.parse(written))
        assertTrue(SettingsText.write(settings, braces = true).startsWith("{"))
    }

    @Test
    fun `braces follow the app's export, or the picks when the box was empty`() {
        assertFalse(SettingsText.usesBraces("\"a\": 1", "{\"b\": 2}"))
        assertTrue(SettingsText.usesBraces("{\"a\": 1}", "\"b\": 2"))
        assertFalse(SettingsText.usesBraces("", picks))
    }

    @Test
    fun `the catalog's picks need settings and a summary, and their revision follows the settings`() {
        fun guide(
            settings: String,
            summary: List<String> = listOf("Keeps the original icon"),
        ) = ManifestGuideDto(recommended = ManifestRecommendedDto(settings, summary)).toDomain().recommended

        assertNotNull(guide(picks))
        assertNull(guide(picks, summary = emptyList()))
        assertNull(guide(""))
        assertNull(guide("not settings"))
        // spacing alone doesn't make a new set; a changed value does
        assertEquals(guide(picks)!!.revision, guide(picks.replace(": ", ":"))!!.revision)
        assertNotEquals(guide(picks)!!.revision, guide(picks.replace("android_reel", "android_vr"))!!.revision)
    }
}
