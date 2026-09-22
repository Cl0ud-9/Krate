package dev.cl0ud9.manager.domain.repository

import dev.cl0ud9.manager.domain.model.AppProfile
import kotlinx.coroutines.flow.Flow

// remembers the version THIS manager actually installed for each app, keyed by package name -
// deliberately separate from ActivityLogRepository (capped at a small number of newest entries,
// display-oriented) since this needs to be permanent, one entry per app, and is read to drive real
// update-availability decisions, not just shown to the user
interface ManagerBaselineStore {
    fun observeBaselines(): Flow<Map<String, String>>

    suspend fun recordInstall(
        packageName: String,
        versionName: String,
    )
}

// the baseline to actually compare against: the recorded one if the manager has ever completed a
// real install for this app, otherwise the best available guess. A package can arrive already
// diverged - self-updated outside this app entirely (MicroG RE's own in-app "hide icon" toggle
// installs its own beta build, for example) - before this app ever recorded anything for it,
// including on first launch after this feature ships at all. If the live-installed version is one
// the catalog recognizes, it's trustworthy on its own; if not, the live version is exactly what we
// don't trust, so the fallback instead guesses from the catalog's OWN memory - the oldest version
// it still retains (app.artifacts is newest-first) is the most conservative honest guess available,
// since anything the catalog has already forgotten couldn't have been what was actually installed
// through it anyway. Deliberately not persisted when falling back to this guess - it's cheap to
// recompute, and persisting it would lock in a guess instead of a fact the moment a real install
// happens and overwrites it for real
fun effectiveBaseline(
    recordedBaseline: String?,
    app: AppProfile,
    installedVersionName: String?,
): String? =
    when {
        recordedBaseline != null -> recordedBaseline
        installedVersionName == null -> null
        app.artifacts.any { it.versionName == installedVersionName } -> installedVersionName
        else -> app.artifacts.lastOrNull()?.versionName
    }
