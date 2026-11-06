package dev.cl0ud9.krate.platform.autoupdate

import java.text.DateFormat
import java.util.Date

// how long automatic updates can be held back at once: a day, a week, or until the user turns them back on
enum class PauseLength(
    val label: String,
    val millis: Long,
) {
    DAY("For a day", DAY_MS),
    WEEK("For a week", DAYS_IN_WEEK * DAY_MS),
    UNTIL_RESUMED("Until I turn them back on", 0L),
}

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val DAYS_IN_WEEK = 7

// within this the end of a pause reads as a weekday and a time, beyond it as a date
private const val WEEKDAY_WINDOW_DAYS = 6

// a pause with no end is stored as a time far enough off never to arrive, and small enough that adding to it can't
// overflow
private const val OPEN_ENDED_PAUSE = Long.MAX_VALUE / 4

// a pause is just the moment it ends; nothing is stored once that has passed, so an old one can never hold updates back
internal fun pausedUntil(
    nowMillis: Long,
    length: PauseLength,
): Long = if (length == PauseLength.UNTIL_RESUMED) OPEN_ENDED_PAUSE else nowMillis + length.millis

// a pause only the user can end
internal fun isOpenEnded(untilMillis: Long): Boolean = untilMillis >= OPEN_ENDED_PAUSE

internal fun isPausedAt(
    untilMillis: Long,
    nowMillis: Long,
): Boolean = untilMillis > nowMillis

// "Fri 18:00" for a pause ending within the week, the date and time beyond that
internal fun pauseEndText(
    untilMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    val sameWeek = untilMillis - nowMillis < WEEKDAY_WINDOW_DAYS * DAY_MS
    val format =
        if (sameWeek) {
            java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale.getDefault())
        } else {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        }
    return format.format(Date(untilMillis))
}
