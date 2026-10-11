package dev.cl0ud9.krate.domain

import dev.cl0ud9.krate.data.catalog.ManifestGuideDto
import dev.cl0ud9.krate.data.catalog.ManifestWalkStepDto
import dev.cl0ud9.krate.data.catalog.toDomain
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.ArtifactInfo
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.model.SupportStatus
import dev.cl0ud9.krate.domain.suggest.LOOK_COOLDOWN_MS
import dev.cl0ud9.krate.domain.suggest.SuggestionKind
import dev.cl0ud9.krate.domain.suggest.SuggestionMemory
import dev.cl0ud9.krate.domain.suggest.YOURS
import dev.cl0ud9.krate.domain.suggest.appsForNeed
import dev.cl0ud9.krate.domain.suggest.needsFor
import dev.cl0ud9.krate.domain.suggest.pickSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 20_000L * day

    @Suppress("LongParameterList")
    private fun app(
        id: String,
        pitch: String? = "A pitch for $id",
        needs: List<String> = listOf("Music"),
        companions: List<String> = emptyList(),
        tracked: Boolean = false,
        status: SupportStatus = SupportStatus.SUPPORTED,
        withBuild: Boolean = true,
    ) = AppProfile(
        id = id,
        displayName = id.uppercase(),
        packageName = "pkg.$id",
        supportStatus = status,
        installationMode = InstallationMode.UPDATE,
        dependencyIds = emptyList(),
        releaseNotes = null,
        enabled = true,
        artifacts = if (withBuild) listOf(ArtifactInfo("1.0", "https://x/$id.apk", "sha", "cert")) else emptyList(),
        pitch = pitch,
        needs = needs,
        suggestWhenInstalled = companions,
        trackedRepo = if (tracked) "o/$id" else null,
    )

    private fun memory(
        seen: Set<String>? = emptySet(),
        down: Set<String> = emptySet(),
        looked: Map<String, Long> = emptyMap(),
        enabled: Boolean = true,
    ) = SuggestionMemory(enabled, down, looked, seen)

    @Test
    fun `nothing is suggested before the shelf has been noted, or when switched off`() {
        val apps = listOf(app("a"))
        assertNull(pickSuggestion(apps, emptySet(), memory(seen = null), now))
        assertNull(pickSuggestion(apps, emptySet(), memory(seen = setOf("a"), enabled = false), now))
    }

    @Test
    fun `a new arrival comes first, and an app already seen is only worth a try`() {
        val apps = listOf(app("old"), app("fresh"))
        val picked = pickSuggestion(apps, emptySet(), memory(seen = setOf("old")), now)!!
        assertEquals("fresh", picked.app.id)
        assertEquals(SuggestionKind.NEW, picked.kind)
        val later = pickSuggestion(apps, emptySet(), memory(seen = setOf("old", "fresh")), now)!!
        assertEquals(SuggestionKind.WORTH_A_TRY, later.kind)
    }

    @Test
    fun `installed, tracked, turned down, pitchless, unsupported and build-less apps are never suggested`() {
        val apps =
            listOf(
                app("installed"),
                app("tracked", tracked = true),
                app("down"),
                app("quiet", pitch = null),
                app("beta", status = SupportStatus.BETA),
                app("empty", withBuild = false),
            )
        val seen = apps.map { it.id }.toSet()
        assertNull(pickSuggestion(apps, setOf("pkg.installed"), memory(seen, down = setOf("down")), now))
    }

    @Test
    fun `an app looked at is left alone for a week, then comes back`() {
        val apps = listOf(app("a"))
        val seen = setOf("a")
        assertNull(pickSuggestion(apps, emptySet(), memory(seen, looked = mapOf("a" to now - day)), now))
        assertNotNull(
            pickSuggestion(apps, emptySet(), memory(seen, looked = mapOf("a" to now - LOOK_COOLDOWN_MS)), now),
        )
    }

    @Test
    fun `an app that fits what's installed wins, and says which one`() {
        val apps = listOf(app("generic"), app("fits", companions = listOf("com.spotify.music")))
        val picked =
            pickSuggestion(apps, setOf("com.spotify.music"), memory(setOf("generic", "fits")), now) { "Spotify" }!!
        assertEquals("fits", picked.app.id)
        assertEquals("Spotify", picked.becauseOf)
    }

    @Test
    fun `the choice holds steady through a day and moves on the next`() {
        val apps = listOf(app("a"), app("b"), app("c"))
        val seen = setOf("a", "b", "c")
        val today = pickSuggestion(apps, emptySet(), memory(seen), now)!!.app.id
        assertEquals(today, pickSuggestion(apps, emptySet(), memory(seen), now + day / 2)!!.app.id)
        val days = (0..5).map { pickSuggestion(apps, emptySet(), memory(seen), now + it * day)!!.app.id }.toSet()
        assertTrue(days.size > 1)
    }

    @Test
    fun `chips only appear for a shelf big enough to sort, with at least two needs`() {
        val small = listOf(app("a"), app("b", needs = listOf("Storage")))
        assertTrue(needsFor(small).isEmpty())
        val big =
            listOf(
                app("a"),
                app("b", needs = listOf("Storage")),
                app("c"),
                app("d", needs = listOf("Videos")),
                app("e"),
                app("f", tracked = true, needs = emptyList()),
            )
        assertEquals(listOf("Music", "Storage", "Videos", YOURS), needsFor(big))
        val oneNeed = List(6) { app("x$it") }
        assertTrue(needsFor(oneNeed).isEmpty())
    }

    @Test
    fun `a chip narrows to its apps, yours to the tracked ones, and none to everything`() {
        val apps = listOf(app("a"), app("b", needs = listOf("Storage")), app("t", tracked = true, needs = emptyList()))
        assertEquals(3, appsForNeed(apps, null).size)
        assertEquals(listOf("b"), appsForNeed(apps, "Storage").map { it.id })
        assertEquals(listOf("t"), appsForNeed(apps, YOURS).map { it.id })
    }

    @Test
    fun `a link meant for the app itself needs a scheme of its own, and only then does confirm count`() {
        fun steps(vararg step: ManifestWalkStepDto) =
            ManifestGuideDto(walkthrough = step.toList()).toDomain().walkthrough
        val kept =
            steps(
                ManifestWalkStepDto(
                    "Store",
                    "Add it",
                    link = "mihon://extension-store?url=x",
                    inApp = true,
                    confirm = "Add",
                ),
                ManifestWalkStepDto("Web", "Not its own", link = "https://example.org", inApp = true, confirm = "Add"),
                ManifestWalkStepDto("File", "Reaches files", link = "file:///sdcard/x", inApp = true),
                ManifestWalkStepDto("Intent", "Reaches anything", link = "intent://x#Intent;end", inApp = true),
                ManifestWalkStepDto("Loose", "Confirm without in-app", link = "https://example.org", confirm = "Add"),
                ManifestWalkStepDto("Open", "Starts in the app", openApp = true),
            )
        assertEquals("mihon://extension-store?url=x", kept[0].link)
        assertEquals("Add", kept[0].confirm)
        assertNull(kept[1].link)
        assertNull(kept[2].link)
        assertNull(kept[3].link)
        // tapping something for someone only makes sense for a link opened inside the app
        assertNull(kept[4].confirm)
        assertTrue(kept[5].openApp)
    }

    @Test
    fun `a walkthrough step needs words, and only opens a web or Telegram link`() {
        fun steps(vararg step: ManifestWalkStepDto) =
            ManifestGuideDto(walkthrough = step.toList()).toDomain().walkthrough
        val kept =
            steps(
                ManifestWalkStepDto("One", "Do this", link = "https://example.org", linkLabel = "Open"),
                ManifestWalkStepDto("Two", "Then this", link = "tg://resolve?domain=x"),
                ManifestWalkStepDto("Three", "And this", link = "intent://evil#Intent;end", copy = "text"),
                ManifestWalkStepDto("", "No title"),
                ManifestWalkStepDto("No detail", ""),
            )
        assertEquals(listOf("One", "Two", "Three"), kept.map { it.title })
        assertEquals("https://example.org", kept[0].link)
        assertEquals("tg://resolve?domain=x", kept[1].link)
        assertNull(kept[2].link)
        assertEquals("text", kept[2].copy)
    }
}
