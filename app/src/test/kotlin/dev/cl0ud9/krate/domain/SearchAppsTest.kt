package dev.cl0ud9.krate.domain

import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.model.SupportStatus
import dev.cl0ud9.krate.domain.suggest.searchApps
import dev.cl0ud9.krate.domain.suggest.starterApps
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchAppsTest {
    private fun app(
        id: String,
        name: String,
        pitch: String? = null,
        needs: List<String> = emptyList(),
    ) = AppProfile(
        id = id,
        displayName = name,
        packageName = "com.example.$id",
        supportStatus = SupportStatus.SUPPORTED,
        installationMode = InstallationMode.UPDATE,
        dependencyIds = emptyList(),
        releaseNotes = null,
        enabled = true,
        artifacts = emptyList(),
        pitch = pitch,
        needs = needs,
    )

    private val apps =
        listOf(
            app("reader", "Manga Reader", "Read manga offline", listOf("Reading")),
            app("cleaner", "Cache Cleaner", "Spring cleaning", listOf("Storage")),
            app("player", "Lossless Player", needs = listOf("Music")),
        )

    @Test
    fun `nothing typed leaves everything`() {
        assertEquals(apps, searchApps(apps, ""))
        assertEquals(apps, searchApps(apps, "   "))
    }

    @Test
    fun `matches the name whatever the case`() {
        assertEquals(listOf("reader"), searchApps(apps, "MANGA").map { it.id })
    }

    @Test
    fun `matches the pitch, the package and the needs`() {
        assertEquals(listOf("cleaner"), searchApps(apps, "spring").map { it.id })
        assertEquals(listOf("player"), searchApps(apps, "example.player").map { it.id })
        assertEquals(listOf("player"), searchApps(apps, "music").map { it.id })
    }

    @Test
    fun `every word has to be found, in any order`() {
        assertEquals(listOf("reader"), searchApps(apps, "offline manga").map { it.id })
        assertEquals(emptyList<String>(), searchApps(apps, "manga cleaner"))
    }

    private fun buildable(
        id: String,
        needs: List<String>,
        pitch: String? = "A pitch",
    ) = app(id, id, pitch, needs).copy(
        artifacts =
            listOf(
                dev.cl0ud9.krate.domain.model
                    .ArtifactInfo("1.0", "u", "s", "c", versionCode = 1),
            ),
    )

    @Test
    fun `starters are different needs first, and never installed or without a pitch`() {
        val shelf =
            listOf(
                buildable("a", listOf("Music")),
                buildable("b", listOf("Music")),
                buildable("c", listOf("Storage")),
                buildable("d", listOf("Reading")),
                buildable("e", listOf("Games"), pitch = null),
            )

        assertEquals(listOf("a", "c", "d"), starterApps(shelf, emptySet()).map { it.id })
        assertEquals(listOf("b", "c", "d"), starterApps(shelf, setOf("com.example.a")).map { it.id })
    }

    @Test
    fun `fewer than three apps on offer gives fewer starters`() {
        assertEquals(listOf("only"), starterApps(listOf(buildable("only", emptyList())), emptySet()).map { it.id })
        assertEquals(emptyList<String>(), starterApps(emptyList(), emptySet()).map { it.id })
    }
}
