package dev.cl0ud9.krate.ui.home

import dev.cl0ud9.krate.data.activity.DOWNLOAD_FAILURE_PREFIX
import dev.cl0ud9.krate.data.activity.INSTALL_FAILURE_PREFIX
import dev.cl0ud9.krate.domain.model.ActivityAction
import dev.cl0ud9.krate.domain.model.ActivityEntry

private val NETWORK_HINTS = listOf("internet", "connection", "timed out", "interrupted")

// a build that arrived whole but failed Krate's checks: another try fetches the same build, so no retry is offered
private val CHECK_HINTS = listOf("signed by the expected developer", "isn't the one this page is for")

// one short line for a Recent activity row; a failure gets a light hint at the cause rather than the full error
internal fun activitySummary(
    entry: ActivityEntry,
    label: String,
    canRetry: Boolean,
): String {
    if (entry.action != ActivityAction.FAILED) return entry.detail?.let { "$label. $it" } ?: label
    val detail = entry.detail.orEmpty()
    val turnedAway = CHECK_HINTS.any { detail.contains(it, ignoreCase = true) }
    val line =
        when {
            turnedAway -> "Turned away at Krate's checks."
            detail.contains("limiting", ignoreCase = true) -> "GitHub asked Krate to slow down."
            NETWORK_HINTS.any { detail.contains(it, ignoreCase = true) } -> "No signal, no parcel."
            detail.startsWith(DOWNLOAD_FAILURE_PREFIX) -> "Dropped it on the way over."
            detail.startsWith(INSTALL_FAILURE_PREFIX) -> "It didn't quite unpack."
            else -> "That one didn't land."
        }
    return if (canRetry && !turnedAway) "$line Tap to retry." else line
}
