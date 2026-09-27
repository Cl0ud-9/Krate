package dev.cl0ud9.krate.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
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
    fun `every line comes up once before any repeats`() {
        val picker = LinePicker(Random(9))
        val lines = Moment.GREETING.lines
        val firstRound = List(lines.size) { picker.pick("greeting", lines) }
        assertEquals(lines.toSet(), firstRound.toSet())
    }

    @Test
    fun `a pool keeps its place across launches through its memory`() {
        val memory = InMemoryLineMemory()
        val lines = Moment.GREETING.lines
        // a new picker per pick is a fresh process per launch
        val picks = List(lines.size) { seed -> LinePicker(Random(seed), memory).pick("greeting", lines) }
        assertEquals(lines.size, picks.toSet().size)
    }

    @Test
    fun `a quick return, a long absence and the day's first open each get their own greeting`() {
        val monday = LocalDate.of(2026, 9, 28)
        val base =
            GreetingContext(monday, hour = 9, sinceLastOpenMillis = null, firstOpenToday = true, installedOn = null)
        val quick = base.copy(sinceLastOpenMillis = 60_000L, firstOpenToday = false)
        val quickPicks = List(200) { seed -> greetingMomentFor(quick, Random(seed)) }.toSet()
        assertTrue(Moment.QUICK_RETURN in quickPicks)
        val away = base.copy(sinceLastOpenMillis = 10L * 24 * 60 * 60 * 1000)
        assertEquals(Moment.LONG_ABSENCE, greetingMomentFor(away, Random(1)))
        assertEquals(Moment.GREETING_MORNING, greetingMomentFor(base, Random(1)))
    }

    @Test
    fun `a special day beats every other greeting`() {
        val newYear = GreetingContext(LocalDate.of(2027, 1, 1), 9, 60_000L, false, null)
        repeat(50) { seed -> assertEquals(Moment.NEW_YEAR, greetingMomentFor(newYear, Random(seed))) }
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
        assertEquals(Moment.GREETING_NIGHT, timeOfDay(2))
        assertEquals(Moment.GREETING_MORNING, timeOfDay(5))
        assertEquals(Moment.GREETING_MORNING, timeOfDay(11))
        assertEquals(Moment.GREETING_AFTERNOON, timeOfDay(12))
        assertEquals(Moment.GREETING_EVENING, timeOfDay(17))
        assertEquals(Moment.GREETING_NIGHT, timeOfDay(22))
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

    @Test
    fun `every plain line is short and ends like a sentence`() {
        Moment.entries.forEach { moment ->
            assertTrue(moment.name, moment.plain.length <= MAX_LINE_LENGTH)
            assertTrue(moment.name, moment.plain.last() in ".?!")
        }
    }

    @Test
    fun `special days take over the greeting`() {
        val installed = LocalDate.of(2026, 9, 27)
        assertEquals(Moment.NEW_YEAR, specialDay(LocalDate.of(2027, 1, 1), installed))
        assertEquals(Moment.CHRISTMAS, specialDay(LocalDate.of(2026, 12, 25), installed))
        assertEquals(Moment.KRATE_ANNIVERSARY, specialDay(LocalDate.of(2027, 9, 27), installed))
        assertEquals(Moment.KRATE_ANNIVERSARY, specialDay(LocalDate.of(2029, 9, 27), installed))
    }

    @Test
    fun `ordinary days, the install day itself and an unknown install date have no special greeting`() {
        val installed = LocalDate.of(2026, 9, 27)
        assertNull(specialDay(LocalDate.of(2026, 9, 27), installed))
        assertNull(specialDay(LocalDate.of(2027, 9, 28), installed))
        assertNull(specialDay(LocalDate.of(2027, 3, 14), null))
    }

    private companion object {
        const val MAX_LINE_LENGTH = 40
    }
}
