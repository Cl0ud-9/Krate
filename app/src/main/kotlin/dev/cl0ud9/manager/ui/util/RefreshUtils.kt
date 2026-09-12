package dev.cl0ud9.manager.ui.util

import kotlinx.coroutines.delay

// PullToRefreshBox plays its own entrance/exit animation for the indicator. Flipping isRefreshing
// back to false before that animation has had time to play leaves the indicator's internal pull
// state unable to fully settle, so a second pull right after a near-instant refresh (a fast network
// call, or one served from cache) often fails to re-arm - the exact "works once, then stops
// responding" report this fixes. A guaranteed minimum visible duration also reads as more deliberate
// than an instant flash on a fast refresh, and doesn't cost anything on a genuinely slow one.
private const val MIN_REFRESH_VISIBLE_MS = 500L

suspend fun <T> withMinimumDuration(
    minMillis: Long = MIN_REFRESH_VISIBLE_MS,
    block: suspend () -> T,
): T {
    val start = System.currentTimeMillis()
    val result = block()
    val elapsed = System.currentTimeMillis() - start
    if (elapsed < minMillis) delay(minMillis - elapsed)
    return result
}
