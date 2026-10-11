package dev.cl0ud9.krate.domain.suggest

import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.SupportStatus
import dev.cl0ud9.krate.domain.model.isTracked
import dev.cl0ud9.krate.domain.model.latestArtifact

// why an app is being put forward: it just joined the shelf, or it simply fits
enum class SuggestionKind {
    NEW,
    WORTH_A_TRY,
}

// one app to put in front of someone, and the app of theirs that made it a fit, if one did
data class Suggestion(
    val app: AppProfile,
    val kind: SuggestionKind,
    val becauseOf: String? = null,
)

// what's been decided so far, all kept on the phone. seen is null until Krate has noted what was on the shelf when
// suggestions first ran: nothing already there counts as new
data class SuggestionMemory(
    val enabled: Boolean = true,
    val turnedDown: Set<String> = emptySet(),
    val lookedAt: Map<String, Long> = emptyMap(),
    val seen: Set<String>? = null,
)

// an app someone had a look at isn't put forward again for a week
internal const val LOOK_COOLDOWN_MS = 7L * 24 * 60 * 60 * 1000
private const val DAY_MS = 24L * 60 * 60 * 1000

// the one app worth mentioning right now, or nothing. [apps] are the ones this person can see; [installed] the
// packages already on the phone. Never an app that's installed, tracked, turned down, looked at this week or without
// a pitch and a build to install. A new arrival comes first, then the apps that fit what's installed, then the rest in
// a rotation that moves on each day, so the card is steady within a day and fresh across them
fun pickSuggestion(
    apps: List<AppProfile>,
    installed: Set<String>,
    memory: SuggestionMemory,
    nowMillis: Long,
    labelOf: (String) -> String? = { null },
): Suggestion? {
    val seen = memory.seen?.takeIf { memory.enabled } ?: return null
    val candidates =
        apps
            .filter { it.canBeSuggested(installed, memory, nowMillis) }
            .sortedBy { it.id }
    val fresh = candidates.firstOrNull { it.id !in seen }
    val fitting = candidates.filter { app -> app.suggestWhenInstalled.any { it in installed } }
    val pool = fitting.ifEmpty { candidates }
    val rotated = pool.getOrNull(((nowMillis / DAY_MS) % pool.size.coerceAtLeast(1)).toInt())
    return when {
        fresh != null -> Suggestion(fresh, SuggestionKind.NEW, becauseOf(fresh, installed, labelOf))
        rotated != null -> Suggestion(rotated, SuggestionKind.WORTH_A_TRY, becauseOf(rotated, installed, labelOf))
        else -> null
    }
}

private fun AppProfile.canBeSuggested(
    installed: Set<String>,
    memory: SuggestionMemory,
    nowMillis: Long,
): Boolean {
    val looked = memory.lookedAt[id]
    return pitch != null &&
        !isTracked &&
        enabled &&
        supportStatus == SupportStatus.SUPPORTED &&
        latestArtifact != null &&
        packageName !in installed &&
        id !in memory.turnedDown &&
        (looked == null || nowMillis - looked >= LOOK_COOLDOWN_MS)
}

private fun becauseOf(
    app: AppProfile,
    installed: Set<String>,
    labelOf: (String) -> String?,
): String? = app.suggestWhenInstalled.firstOrNull { it in installed }?.let(labelOf)

// the needs worth a chip: each one the shelf has an app for, in the order they first appear, with a chip for apps
// someone added themselves when there are any. Nothing at all unless the shelf is big enough to need sorting
fun needsFor(
    apps: List<AppProfile>,
    minApps: Int = MIN_APPS_FOR_CHIPS,
): List<String> {
    if (apps.size < minApps) return emptyList()
    val needs = apps.flatMap { it.needs }.distinct()
    val yours = if (apps.any { it.isTracked }) listOf(YOURS) else emptyList()
    return if (needs.size < 2) emptyList() else needs + yours
}

// the apps for one chip; everything for none
fun appsForNeed(
    apps: List<AppProfile>,
    need: String?,
): List<AppProfile> =
    when (need) {
        null -> apps
        YOURS -> apps.filter { it.isTracked }
        else -> apps.filter { need in it.needs }
    }

const val YOURS = "Added by you"
const val MIN_APPS_FOR_CHIPS = 6
