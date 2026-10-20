package dev.cl0ud9.krate.domain

import dev.cl0ud9.krate.domain.links.LinkState
import dev.cl0ud9.krate.domain.links.OpenLinks
import dev.cl0ud9.krate.domain.links.declaredHosts
import dev.cl0ud9.krate.domain.links.missing
import dev.cl0ud9.krate.domain.links.stillOff
import dev.cl0ud9.krate.domain.links.wantedHosts
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenLinksTest {
    private val declared = setOf("open.spotify.com", "spotify.link", "radio.example.com", "video.example.com")
    private val music = "app.music"
    private val lastWave = OpenLinks(listOf("open.spotify.com", "spotify.link"), mapOf("radio.example.com" to music))

    @Test
    fun `an app takes the addresses it owns, and a shared one only while the better placed app is missing`() {
        assertEquals(
            listOf("open.spotify.com", "spotify.link", "radio.example.com"),
            wantedHosts(lastWave, installed = emptySet(), declared = declared),
        )
        assertEquals(
            listOf("open.spotify.com", "spotify.link"),
            wantedHosts(lastWave, installed = setOf(music), declared = declared),
        )
    }

    @Test
    fun `a catalog entry never reaches past what the app itself declares`() {
        val greedy = OpenLinks(listOf("open.spotify.com", "bank.example"))

        assertEquals(listOf("open.spotify.com"), wantedHosts(greedy, emptySet(), declared))
    }

    @Test
    fun `case and repeats make no difference`() {
        val links = OpenLinks(listOf("Video.example.com", "video.example.com"))

        assertEquals(listOf("video.example.com"), wantedHosts(links, emptySet(), declared))
    }

    @Test
    fun `only what isn't already on is left to do`() {
        val states = listOf(LinkState("video.example.com", on = true), LinkState("radio.example.com", on = false))

        assertEquals(listOf("radio.example.com"), missing(listOf("video.example.com", "radio.example.com"), states))
        assertEquals(emptyList<String>(), missing(listOf("video.example.com"), states))
    }

    @Test
    fun `an app with no catalog entry gets only the addresses it declares that nobody else has, without wildcards`() {
        val declared =
            listOf("Open.Example.com", "*.example.com", "taken.example.com", "open.example.com", "localhost", " ")

        assertEquals(
            listOf("open.example.com"),
            declaredHosts(declared, takenByAnother = { it == "taken.example.com" }),
        )
    }

    @Test
    fun `a long list is cut to what a person should be asked to switch on at once`() {
        val many = (1..20).map { "host$it.example.com" }

        assertEquals(6, declaredHosts(many, takenByAnother = { false }).size)
    }

    @Test
    fun `only the addresses Android still has off are left to switch on`() {
        val states = listOf(LinkState("a.example", on = true), LinkState("b.example", on = false))

        assertEquals(listOf("b.example", "c.example"), stillOff(listOf("A.example", "b.example", "c.example"), states))
        assertEquals(emptyList<String>(), stillOff(listOf("a.example"), states))
        assertEquals(listOf("a.example"), stillOff(listOf("a.example"), null))
    }
}
