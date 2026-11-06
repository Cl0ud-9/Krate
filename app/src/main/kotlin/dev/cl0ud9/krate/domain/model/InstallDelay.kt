package dev.cl0ud9.krate.domain.model

private const val HOUR_MS = 60L * 60 * 1000
private const val THREE_HOURS_MS = 3 * HOUR_MS
private const val SIX_HOURS_MS = 6 * HOUR_MS
private const val DAY_MS = 24 * HOUR_MS
private const val THREE_DAYS_MS = 3 * DAY_MS
private const val WEEK_MS = 7 * DAY_MS

// how long a new release waits before installing by itself, counted from when it came out. The wait is a safety net:
// a broken build is usually caught and withdrawn within a day or two, before it reaches anyone who waited
enum class InstallDelay(
    val millis: Long,
) {
    RIGHT_AWAY(0L),
    THREE_HOURS(THREE_HOURS_MS),
    SIX_HOURS(SIX_HOURS_MS),
    ONE_DAY(DAY_MS),
    THREE_DAYS(THREE_DAYS_MS),
    ONE_WEEK(WEEK_MS),
    ;

    companion object {
        val DEFAULT = ONE_DAY
    }
}

// the label a setting shows, and the words that finish "New versions install ..."
fun InstallDelay.label(): String =
    when (this) {
        InstallDelay.RIGHT_AWAY -> "Right away"
        InstallDelay.THREE_HOURS -> "After 3 hours"
        InstallDelay.SIX_HOURS -> "After 6 hours"
        InstallDelay.ONE_DAY -> "After a day"
        InstallDelay.THREE_DAYS -> "After 3 days"
        InstallDelay.ONE_WEEK -> "After a week"
    }

// Krate looks for new versions every few hours, so the short waits are "at least", never to the minute
fun InstallDelay.whenText(): String =
    when (this) {
        InstallDelay.RIGHT_AWAY -> "as soon as Krate sees them"
        InstallDelay.THREE_HOURS -> "at least 3 hours after release"
        InstallDelay.SIX_HOURS -> "at least 6 hours after release"
        InstallDelay.ONE_DAY -> "a day after release"
        InstallDelay.THREE_DAYS -> "3 days after release"
        InstallDelay.ONE_WEEK -> "a week after release"
    }
