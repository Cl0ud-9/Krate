package dev.cl0ud9.manager.domain.model

enum class AnnouncementSeverity {
    INFO,
    WARNING,
    CRITICAL,
}

// a curated notice from catalog/announcements.json, shown only while it is listed there - for
// things like an app being discontinued or a temporary alternative being available. appIds empty
// means it is about the manager or the catalog as a whole
data class Announcement(
    val id: String,
    val severity: AnnouncementSeverity,
    val title: String,
    val message: String,
    val appIds: List<String>,
    val actionAppId: String?,
    val expiresAtMillis: Long?,
    val dismissible: Boolean,
)

// an announcement plus the display name of the app its action opens, when it has one
data class AnnouncementItem(
    val announcement: Announcement,
    val actionAppName: String?,
)

fun Announcement.isActive(nowMillis: Long): Boolean = expiresAtMillis == null || expiresAtMillis > nowMillis
