package dev.cl0ud9.manager.domain.updateall

import dev.cl0ud9.manager.domain.dependency.DependencyGraph
import dev.cl0ud9.manager.domain.model.AppProfile

// orders the apps that currently have a pending update so that, among themselves, a dependency
// updates before whatever depends on it - section 23 + 42.21 of the spec (Update All: dependency
// resolution, ordering, sequential deployment). This only reorders apps already in the pending set;
// it does not pull in a not-yet-installed dependency that isn't itself pending an update - that is
// the existing per-app "install this dependency" flow (section 14 + 42.11), a different action.
object UpdateAllPlanner {
    fun order(
        pending: List<AppProfile>,
        catalog: List<AppProfile>,
    ): List<AppProfile> {
        val pendingIds = pending.map { it.id }.toSet()
        val ordered = mutableListOf<AppProfile>()
        val seen = mutableSetOf<String>()

        for (app in pending) {
            // a declared dependency cycle makes a stable order impossible for this app's chain -
            // fall back to just the app itself rather than dropping it from the batch entirely
            val chain = DependencyGraph.installOrder(app, catalog) ?: listOf(app)
            for (candidate in chain) {
                if (candidate.id in pendingIds && seen.add(candidate.id)) {
                    ordered += candidate
                }
            }
        }
        return ordered
    }
}
