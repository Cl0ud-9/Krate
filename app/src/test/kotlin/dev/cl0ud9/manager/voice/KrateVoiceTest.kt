package dev.cl0ud9.manager.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class KrateVoiceTest {
    @Test
    fun `a pool never gives the same line twice in a row`() {
        val picker = LinePicker(Random(7))
        val lines = listOf("a", "b", "c", "d")
        var previous = picker.pick("pool", lines)
        repeat(500) {
            val next = picker.pick("pool", lines)
            assertNotEquals(previous, next)
            previous = next
        }
    }

    @Test
    fun `the last two lines are both skipped when the pool is big enough`() {
        val picker = LinePicker(Random(11))
        val lines = listOf("a", "b", "c", "d", "e")
        val picks = List(500) { picker.pick("pool", lines) }
        picks.windowed(3).forEach { window -> assertEquals(3, window.toSet().size) }
    }

    @Test
    fun `a two-line pool alternates and a one-line pool still answers`() {
        val picker = LinePicker(Random(3))
        val picks = List(10) { picker.pick("pair", listOf("x", "y")) }
        picks.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
        assertEquals("only", picker.pick("single", listOf("only")))
        assertEquals("only", picker.pick("single", listOf("only")))
    }

    @Test
    fun `the previous launch's greeting is avoided`() {
        val lines = Moment.GREETING.lines
        repeat(200) { seed ->
            val picker = LinePicker(Random(seed))
            assertNotEquals(lines[0], picker.pick("greeting", lines, avoid = lines[0]))
        }
    }

    @Test
    fun `pools are separate`() {
        val picker = LinePicker(Random(5))
        val first = picker.pick("one", listOf("a", "b"))
        // a different pool is free to use the same text
        assertTrue(picker.pick("two", listOf(first)) == first)
    }

    @Test
    fun `hours map to the right part of the day`() {
        assertEquals(Moment.GREETING_NIGHT, KrateVoice.timeOfDay(2))
        assertEquals(Moment.GREETING_MORNING, KrateVoice.timeOfDay(5))
        assertEquals(Moment.GREETING_MORNING, KrateVoice.timeOfDay(11))
        assertEquals(Moment.GREETING_AFTERNOON, KrateVoice.timeOfDay(12))
        assertEquals(Moment.GREETING_EVENING, KrateVoice.timeOfDay(17))
        assertEquals(Moment.GREETING_NIGHT, KrateVoice.timeOfDay(22))
    }

    @Test
    fun `every line is short, unique in its pool, and ends like a sentence`() {
        Moment.entries.forEach { moment ->
            assertEquals(moment.name, moment.lines.size, moment.lines.toSet().size)
            moment.lines.forEach { line ->
                assertTrue("$moment: $line", line.length <= MAX_LINE_LENGTH)
                assertTrue("$moment: $line", line.last() in ".?!")
                assertTrue("$moment: $line", '—' !in line)
            }
        }
    }

    private companion object {
        const val MAX_LINE_LENGTH = 40
    }
}
