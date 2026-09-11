package dev.cl0ud9.manager.domain.updateall

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.model.SupportStatus
import org.junit.Assert.assertEquals
import org.junit.Test

// section 23 + 42.21 of the spec: Update All orders pending apps so a dependency updates first
class UpdateAllPlannerTest {
    @Test
    fun `independent pending apps keep their original relative order`() {
        val a = profile("a", dependencyIds = emptyList())
        val b = profile("b", dependencyIds = emptyList())

        val order = UpdateAllPlanner.order(pending = listOf(a, b), catalog = listOf(a, b))

        assertEquals(listOf(a, b), order)
    }

    @Test
    fun `a pending dependency is moved before the app that depends on it`() {
        val dependency = profile("dependency", dependencyIds = emptyList())
        val app = profile("app", dependencyIds = listOf("dependency"))

        // pending list is given app-first, out of dependency order, on purpose
        val order = UpdateAllPlanner.order(pending = listOf(app, dependency), catalog = listOf(app, dependency))

        assertEquals(listOf(dependency, app), order)
    }

    @Test
    fun `a dependency that is not itself pending is not pulled into the batch`() {
        val dependency = profile("dependency", dependencyIds = emptyList())
        val app = profile("app", dependencyIds = listOf("dependency"))

        val order = UpdateAllPlanner.order(pending = listOf(app), catalog = listOf(app, dependency))

        assertEquals(listOf(app), order)
    }

    @Test
    fun `a shared dependency is not duplicated for each app that depends on it`() {
        val dependency = profile("dependency", dependencyIds = emptyList())
        val appA = profile("appA", dependencyIds = listOf("dependency"))
        val appB = profile("appB", dependencyIds = listOf("dependency"))

        val order =
            UpdateAllPlanner.order(
                pending = listOf(appA, appB, dependency),
                catalog = listOf(appA, appB, dependency),
            )

        assertEquals(listOf(dependency, appA, appB), order)
    }

    @Test
    fun `a dependency cycle falls back to the app on its own rather than dropping it`() {
        val appA = profile("a", dependencyIds = listOf("b"))
        val appB = profile("b", dependencyIds = listOf("a"))

        val order = UpdateAllPlanner.order(pending = listOf(appA, appB), catalog = listOf(appA, appB))

        assertEquals(listOf(appA, appB), order)
    }

    private fun profile(
        id: String,
        dependencyIds: List<String>,
    ): AppProfile =
        AppProfile(
            id = id,
            displayName = id,
            packageName = "dev.cl0ud9.$id",
            supportStatus = SupportStatus.SUPPORTED,
            installationMode = InstallationMode.UPDATE,
            dependencyIds = dependencyIds,
            latestVersionName = "1.0.0",
            releaseNotes = null,
            enabled = true,
            artifact = null,
        )
}
