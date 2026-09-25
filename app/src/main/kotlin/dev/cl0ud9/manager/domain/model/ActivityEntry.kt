package dev.cl0ud9.manager.domain.model

enum class ActivityAction {
    INSTALLED,
    UPDATED,
    UNINSTALLED,

    // a download or install that didn't complete - detail says why
    FAILED,
}

// a real local log of what the manager has actually done, backing the Home screen's Recent
// activity section - shown newest first, capped to a small count (see ActivityLogRepository)
data class ActivityEntry(
    val id: String,
    val appId: String,
    val appName: String,
    val action: ActivityAction,
    val timestampMillis: Long,
    val detail: String? = null,
)
