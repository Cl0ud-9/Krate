package dev.cl0ud9.krate.voice

import android.content.Context
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import kotlin.random.Random

private const val QUICK_RETURN_MS = 15 * 60 * 1000L
private const val LONG_ABSENCE_MS = 7 * 24 * 60 * 60 * 1000L
private const val WEEKEND_ODDS = 3
private const val TIME_OF_DAY_ODDS = 4

// what's known about this launch when choosing a greeting
internal data class GreetingContext(
    val today: LocalDate,
    val hour: Int,
    // null on the very first launch
    val sinceLastOpenMillis: Long?,
    val firstOpenToday: Boolean,
    val installedOn: LocalDate?,
)

// a special day wins, then a quick return or a long absence; the day's first open says hello for the time of day
internal fun greetingMomentFor(
    context: GreetingContext,
    random: Random,
): Moment {
    val since = context.sinceLastOpenMillis
    val weekend = context.today.dayOfWeek == DayOfWeek.SATURDAY || context.today.dayOfWeek == DayOfWeek.SUNDAY
    return specialDay(context.today, context.installedOn)
        ?: when {
            since != null && since < QUICK_RETURN_MS && random.nextBoolean() -> Moment.QUICK_RETURN
            since != null && since > LONG_ABSENCE_MS -> Moment.LONG_ABSENCE
            context.firstOpenToday -> timeOfDay(context.hour)
            weekend && random.nextInt(WEEKEND_ODDS) == 0 -> Moment.WEEKEND
            random.nextInt(TIME_OF_DAY_ODDS) == 0 -> timeOfDay(context.hour)
            else -> Moment.GREETING
        }
}

@Suppress("MagicNumber")
internal fun timeOfDay(hour: Int): Moment =
    when (hour) {
        in 5..11 -> Moment.GREETING_MORNING
        in 12..16 -> Moment.GREETING_AFTERNOON
        in 17..21 -> Moment.GREETING_EVENING
        else -> Moment.GREETING_NIGHT
    }

// New Year, Christmas and each yearly anniversary of installing Krate get their own greeting for the whole day
@Suppress("MagicNumber")
internal fun specialDay(
    today: LocalDate,
    installedOn: LocalDate?,
): Moment? =
    when {
        today.month == Month.JANUARY && today.dayOfMonth == 1 -> Moment.NEW_YEAR
        today.month == Month.DECEMBER && today.dayOfMonth == 25 -> Moment.CHRISTMAS
        installedOn != null &&
            today.year > installedOn.year &&
            today.month == installedOn.month &&
            today.dayOfMonth == installedOn.dayOfMonth -> Moment.KRATE_ANNIVERSARY
        else -> null
    }

internal fun installedOn(context: Context): LocalDate? =
    runCatching {
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        Instant.ofEpochMilli(installed).atZone(ZoneId.systemDefault()).toLocalDate()
    }.getOrNull()
