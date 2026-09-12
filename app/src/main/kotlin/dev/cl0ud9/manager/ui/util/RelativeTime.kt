package dev.cl0ud9.manager.ui.util

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

// short relative timestamps for the Recent activity list - deliberately coarse (no seconds), since
// exact timing doesn't matter for "did I just update this"
fun formatRelativeTime(
    timestampMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    val elapsed = (nowMillis - timestampMillis).coerceAtLeast(0L)
    return when {
        elapsed < MINUTE_MS -> "Just now"
        elapsed < HOUR_MS -> pluralize(elapsed / MINUTE_MS, "min")
        elapsed < DAY_MS -> pluralize(elapsed / HOUR_MS, "hr")
        else -> pluralize(elapsed / DAY_MS, "day")
    }
}

private fun pluralize(
    amount: Long,
    unit: String,
): String = "$amount $unit${if (amount == 1L) "" else "s"} ago"
