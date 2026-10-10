package dev.cl0ud9.krate.voice

import android.content.SharedPreferences
import dev.cl0ud9.krate.domain.model.ActivityAction
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

private const val KEY_WEEK = "week"
private const val KEY_WEEK_SEEN = "week_seen"
private const val WEEK_MS = 7 * DAY_MS

// two weeks and a day: enough to look back at the whole of last week, whatever day it is now
private const val WEEK_KEPT_MS = 2 * WEEK_MS + DAY_MS

// what happened last week, for the look back on Home
data class WeekInKrate(
    val installs: Int,
    val updates: Int,
    val failures: Int,
)

// the last couple of weeks of installs, updates and failures, kept beside KrateMemory's counts, and which week's look
// back was put away
object KrateWeek {
    internal fun note(
        store: SharedPreferences,
        edit: SharedPreferences.Editor,
        action: ActivityAction,
        nowMillis: Long,
    ) {
        val week = events(store).filter { (at, _) -> nowMillis - at < WEEK_KEPT_MS } + (nowMillis to action)
        edit.putString(KEY_WEEK, week.joinToString("\n") { (at, what) -> "$at:${what.name}" })
    }

    // last Monday to Sunday, by the phone's clock
    fun lastWeek(now: ZonedDateTime): WeekInKrate {
        val store = KrateMemory.prefs ?: return WeekInKrate(0, 0, 0)
        val thisWeek = weekStart(now)
        val from = thisWeek.minusWeeks(1).toInstant().toEpochMilli()
        val until = thisWeek.toInstant().toEpochMilli()
        val week = events(store).filter { (at, _) -> at in from until until }.map { it.second }
        return WeekInKrate(
            installs = week.count { it == ActivityAction.INSTALLED },
            updates = week.count { it == ActivityAction.UPDATED },
            failures = week.count { it == ActivityAction.FAILED },
        )
    }

    // the week whose look back was put away, so it doesn't come back until the next one
    fun dismissed(now: ZonedDateTime): Boolean = KrateMemory.prefs?.getString(KEY_WEEK_SEEN, null) == weekKey(now)

    fun dismiss(now: ZonedDateTime) {
        KrateMemory.prefs
            ?.edit()
            ?.putString(KEY_WEEK_SEEN, weekKey(now))
            ?.apply()
    }

    private fun weekStart(now: ZonedDateTime): ZonedDateTime =
        now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(now.zone)

    private fun weekKey(now: ZonedDateTime): String = weekStart(now).toLocalDate().toString()

    private fun events(store: SharedPreferences): List<Pair<Long, ActivityAction>> =
        store
            .getString(KEY_WEEK, null)
            ?.split('\n')
            ?.mapNotNull { line ->
                val at = line.substringBefore(':').toLongOrNull()
                val what = ActivityAction.entries.firstOrNull { it.name == line.substringAfter(':') }
                if (at != null && what != null) at to what else null
            }.orEmpty()
}

// "Last week Krate updated 3 apps and installed 1 new app. Nothing went wrong."; null for a week with nothing to tell
fun weekInReviewText(week: WeekInKrate): String? {
    val parts =
        listOfNotNull(
            when (week.updates) {
                0 -> null
                1 -> "updated 1 app"
                else -> "updated ${week.updates} apps"
            },
            when (week.installs) {
                0 -> null
                1 -> "installed 1 new app"
                else -> "installed ${week.installs} new apps"
            },
        )
    if (parts.isEmpty()) return null
    val trouble =
        when (week.failures) {
            0 -> "Nothing went wrong."
            1 -> "One download or install didn't finish."
            else -> "${week.failures} downloads or installs didn't finish."
        }
    return "Last week Krate ${parts.joinToString(" and ")}. $trouble"
}
