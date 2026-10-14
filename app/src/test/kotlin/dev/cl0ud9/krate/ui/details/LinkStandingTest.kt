package dev.cl0ud9.krate.ui.details

import dev.cl0ud9.krate.domain.links.LinkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkStandingTest {
    private val wanted = listOf("a.example", "b.example")

    @Test
    fun `addresses all switched on and held by nobody else are done`() {
        val standing = LinkStanding(wanted, listOf(LinkState("a.example", true), LinkState("b.example", true)))

        assertTrue(standing.todo.isEmpty())
        assertTrue(standing.releases.isEmpty())
    }

    @Test
    fun `an unticked address is to do and to tick`() {
        val standing = LinkStanding(wanted, listOf(LinkState("a.example", true), LinkState("b.example", false)))

        assertEquals(listOf("b.example"), standing.todo)
        assertEquals(listOf("b.example"), standing.toTick)
        assertTrue(standing.releases.isEmpty())
    }

    @Test
    fun `an address ticked here but held by another app is still to do, and that app has to let go`() {
        val standing =
            LinkStanding(
                wanted,
                listOf(LinkState("a.example", true), LinkState("b.example", true)),
                owners = mapOf("a.example" to "com.other.player"),
                ownerNames = mapOf("com.other.player" to "Other Player"),
            )

        assertEquals(listOf("a.example"), standing.todo)
        assertTrue(standing.toTick.isEmpty())
        assertEquals(listOf("com.other.player"), standing.releases)
        assertEquals("Other Player", standing.ownerName("a.example"))
        assertNull(standing.ownerName("b.example"))
    }

    @Test
    fun `one app holding several addresses is asked once`() {
        val standing =
            LinkStanding(
                wanted,
                listOf(LinkState("a.example", false), LinkState("b.example", false)),
                owners = mapOf("a.example" to "com.other.player", "b.example" to "com.other.player"),
                ownerNames = mapOf("com.other.player" to "Other Player"),
            )

        assertEquals(listOf("com.other.player"), standing.releases)
        assertEquals(listOf("Other Player"), standing.releaseNames())
        assertEquals(wanted, standing.toTick)
    }

    @Test
    fun `a holder without a known name is called by its package`() {
        val standing =
            LinkStanding(wanted, emptyList(), owners = mapOf("a.example" to "com.other.player"))

        assertEquals("com.other.player", standing.ownerName("a.example"))
    }

    @Test
    fun `only apps holding something that is still to do are asked to let go`() {
        val standing =
            LinkStanding(
                listOf("a.example"),
                listOf(LinkState("a.example", true)),
                owners = emptyMap(),
            )

        assertTrue(standing.releases.isEmpty())
    }
}
