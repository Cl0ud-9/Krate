package dev.cl0ud9.manager.domain.dependency

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.model.SupportStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// section 14 + 42.11 of the spec: dependencies are generic, resolved dependency-first
class DependencyGraphTest {
    @Test
    fun `an app with no dependencies resolves to itself`() {
        val app = profile("app", dependencyIds = emptyList())

        val order = DependencyGraph.installOrder(app, listOf(app))

        assertEquals(listOf(app), order)
    }

    @Test
    fun `a direct dependency is ordered before the app`() {
        val dependency = profile("dependency", dependencyIds = emptyList())
        val app = profile("app", dependencyIds = listOf("dependency"))

        val order = DependencyGraph.installOrder(app, listOf(app, dependency))

        assertEquals(listOf(dependency, app), order)
    }

    @Test
    fun `transitive dependencies resolve deepest first`() {
        val base = profile("base", dependencyIds = emptyList())
        val middle = profile("middle", dependencyIds = listOf("base"))
        val app = profile("app", dependencyIds = listOf("middle"))

        val order = DependencyGraph.installOrder(app, listOf(app, middle, base))

        assertEquals(listOf(base, middle, app), order)
    }

    @Test
    fun `an unknown dependency id is skipped rather than failing resolution`() {
        val app = profile("app", dependencyIds = listOf("does-not-exist"))

        val order = DependencyGraph.installOrder(app, listOf(app))

        assertEquals(listOf(app), order)
    }

    @Test
    fun `a dependency cycle is detected and reported as unresolvable`() {
        val appA = profile("a", dependencyIds = listOf("b"))
        val appB = profile("b", dependencyIds = listOf("a"))

        val order = DependencyGraph.installOrder(appA, listOf(appA, appB))

        assertNull(order)
    }

    @Test
    fun `directDependencies resolves only the immediate level`() {
        val base = profile("base", dependencyIds = emptyList())
        val middle = profile("middle", dependencyIds = listOf("base"))
        val app = profile("app", dependencyIds = listOf("middle"))

        val direct = DependencyGraph.directDependencies(app, listOf(app, middle, base))

        assertEquals(listOf(middle), direct)
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
