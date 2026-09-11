package dev.cl0ud9.manager.domain.updateall

import dev.cl0ud9.manager.domain.model.AppProfile

// per-app result of an Update All run, section 23 + 42.21 of the spec's "result summary"
data class UpdateAllOutcome(
    val app: AppProfile,
    val succeeded: Boolean,
    val reason: String? = null,
)

sealed interface UpdateAllProgress {
    // one emission per meaningful status change within the current app's download/install pipeline,
    // not just once per app - so the UI can show real progress ("Downloading...", "Confirm in the
    // system dialog...") instead of a single opaque spinner for however long an app takes
    data class Step(
        val currentApp: AppProfile,
        val currentIndex: Int,
        val total: Int,
        val statusLabel: String,
        val completed: List<UpdateAllOutcome>,
    ) : UpdateAllProgress

    data class Finished(
        val outcomes: List<UpdateAllOutcome>,
    ) : UpdateAllProgress
}
