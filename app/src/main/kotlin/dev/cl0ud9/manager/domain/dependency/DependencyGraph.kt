package dev.cl0ud9.manager.domain.dependency

import dev.cl0ud9.manager.domain.model.AppProfile

// resolves an app's declared dependencies (and their own, if any) into dependency-first install
// order, section 14 + 42.11 of the spec - microG RE is just one example dependency an app can
// declare, not a special case the engine knows about
object DependencyGraph {
    // [deepest dependency, ..., app] with app last, or null if the catalog declares a dependency cycle
    fun installOrder(
        app: AppProfile,
        catalog: List<AppProfile>,
    ): List<AppProfile>? {
        val byId = catalog.associateBy { it.id }
        val order = mutableListOf<AppProfile>()
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()

        fun visit(current: AppProfile): Boolean {
            if (current.id in visited) return true
            val isCycle = current.id in visiting
            visiting += current.id
            val resolved =
                !isCycle &&
                    current.dependencyIds.all { dependencyId ->
                        val dependency = byId[dependencyId]
                        dependency == null || visit(dependency)
                    }
            if (resolved) {
                visiting -= current.id
                visited += current.id
                order += current
            }
            return resolved
        }

        return if (visit(app)) order else null
    }

    // direct dependencies only, resolved against the catalog - unknown ids are silently skipped since
    // the manifest is the source of truth and may not have surfaced every declared dependency yet
    fun directDependencies(
        app: AppProfile,
        catalog: List<AppProfile>,
    ): List<AppProfile> {
        val byId = catalog.associateBy { it.id }
        return app.dependencyIds.mapNotNull { byId[it] }
    }
}
