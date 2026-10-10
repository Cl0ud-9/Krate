package dev.cl0ud9.krate.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class VoiceExtrasTest {
    @Test
    fun `festivals show only on their day and only where they're celebrated`() {
        val diwali = LocalDate.of(2027, 10, 29)
        assertEquals(Moment.DIWALI, festivalOn(diwali, "IN"))
        assertNull(festivalOn(diwali, "US"))
        assertNull(festivalOn(diwali.plusDays(1), "IN"))
        assertNull(festivalOn(diwali, null))
        assertEquals(Moment.LUNAR_NEW_YEAR, festivalOn(LocalDate.of(2028, 1, 26), "SG"))
    }

    @Test
    fun `eid also counts the day after, for the moon sighting`() {
        val eid = LocalDate.of(2028, 2, 26)
        assertEquals(Moment.EID, festivalOn(eid, "AE"))
        assertEquals(Moment.EID, festivalOn(eid.plusDays(1), "AE"))
        assertNull(festivalOn(eid.plusDays(2), "AE"))
    }

    @Test
    fun `a festival takes over the greeting`() {
        val holi = GreetingContext(LocalDate.of(2027, 3, 22), 9, 60_000L, false, null, region = "IN")
        repeat(50) { seed -> assertEquals(Moment.HOLI, greetingMomentFor(holi, Random(seed))) }
    }

    @Test
    fun `a low battery or a night charge sometimes gets its own greeting, never otherwise`() {
        val base = GreetingContext(LocalDate.of(2026, 10, 7), 23, 2 * 60 * 60 * 1000L, false, null)
        val low = base.copy(battery = BatteryReading(percent = 8, charging = false))
        val charging = base.copy(battery = BatteryReading(percent = 60, charging = true))
        assertTrue(Moment.LOW_BATTERY in List(100) { greetingMomentFor(low, Random(it)) })
        assertTrue(Moment.CHARGING_AT_NIGHT in List(100) { greetingMomentFor(charging, Random(it)) })
        val calm = base.copy(battery = BatteryReading(percent = 80, charging = false))
        val picks = List(100) { greetingMomentFor(calm, Random(it)) }
        assertTrue(Moment.LOW_BATTERY !in picks && Moment.CHARGING_AT_NIGHT !in picks)
    }

    @Test
    fun `the size of an update comes from its version numbers`() {
        assertEquals(Moment.UPDATE_MAJOR, updateSizeMoment("1.9.3", "2.0.0"))
        assertEquals(Moment.UPDATE_SMALL, updateSizeMoment("4.2.3", "v4.2.4"))
        assertNull(updateSizeMoment("4.2.3", "4.3.0"))
        assertNull(updateSizeMoment("nightly", "2.0"))
        assertNull(updateSizeMoment(null, "2.0"))
    }

    @Test
    fun `ordinals read properly`() {
        assertEquals(
            listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "112th"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 112).map(::ordinal),
        )
    }

    @Test
    fun `the week in review says what happened, and nothing for a quiet week`() {
        assertNull(weekInReviewText(WeekInKrate(installs = 0, updates = 0, failures = 2)))
        assertEquals(
            "Last week Krate updated 3 apps and installed 1 new app. Nothing went wrong.",
            weekInReviewText(WeekInKrate(installs = 1, updates = 3, failures = 0)),
        )
        assertEquals(
            "Last week Krate updated 1 app. One download or install didn't finish.",
            weekInReviewText(WeekInKrate(installs = 0, updates = 1, failures = 1)),
        )
    }

    @Test
    fun `a new line avoids echoing the words of the last few`() {
        val picker = LinePicker(Random(4))
        picker.pick("first", listOf("Fresh apps on the shelf."))
        repeat(20) {
            val next = LinePicker(Random(it)).apply { pick("first", listOf("Fresh apps on the shelf.")) }
            val line = next.pick("second", listOf("Fresh morning, fresh apps.", "Good to see you."))
            assertEquals("Good to see you.", line)
        }
    }

    @Test
    fun `key words skip the little ones and Krate itself`() {
        assertEquals(setOf("shelf", "looking", "good"), keyWords("The Krate's shelf is looking good."))
    }

    @Test
    fun `every new pool keeps to the voice rules`() {
        listOf(
            Moment.OFFLINE,
            Moment.LOW_BATTERY,
            Moment.CHARGING_AT_NIGHT,
            Moment.RARE,
            Moment.SECRET,
            Moment.DIWALI,
            Moment.HOLI,
            Moment.EID,
            Moment.LUNAR_NEW_YEAR,
            Moment.UPDATE_MAJOR,
            Moment.UPDATE_SMALL,
            Moment.DEVELOPER_THANKS,
            Moment.WEEKLY,
        ).forEach { moment ->
            moment.lines.forEach { line -> assertTrue("$moment: $line", '!' !in line) }
        }
    }
}
