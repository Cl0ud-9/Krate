package dev.cl0ud9.manager.ui.home

import dev.cl0ud9.manager.data.activity.DOWNLOAD_FAILURE_PREFIX
import dev.cl0ud9.manager.data.activity.INSTALL_FAILURE_PREFIX
import dev.cl0ud9.manager.domain.model.ActivityAction
import dev.cl0ud9.manager.domain.model.ActivityEntry

private val NETWORK_HINTS = listOf("internet", "connection", "timed out", "interrupted")

// one short line for a Recent activity row; a failure gets a light hint at the cause rather than the full error
internal fun activitySummary(
    entry: ActivityEntry,
    label: String,
    canRetry: Boolean,
): String {
    if (entry.action != ActivityAction.FAILED) return entry.detail?.let { "$label. $it" } ?: label
    val detail = entry.detail.orEmpty()
    val line =
        when {
            detail.contains("limiting", ignoreCase = true) -> "GitHub asked Krate to slow down."
            NETWORK_HINTS.any { detail.contains(it, ignoreCase = true) } -> "No signal, no parcel."
            detail.startsWith(DOWNLOAD_FAILURE_PREFIX) -> "Dropped it on the way over."
            detail.startsWith(INSTALL_FAILURE_PREFIX) -> "It didn't quite unpack."
            else -> "That one didn't land."
        }
    return if (canRetry) "$line Tap to retry." else line
}
