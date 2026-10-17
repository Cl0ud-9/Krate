package dev.cl0ud9.krate.domain

import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.model.SupportStatus
import dev.cl0ud9.krate.domain.suggest.searchApps
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
}
