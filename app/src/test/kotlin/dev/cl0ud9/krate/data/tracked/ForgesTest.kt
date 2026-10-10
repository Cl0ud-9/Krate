package dev.cl0ud9.krate.data.tracked

import dev.cl0ud9.krate.domain.model.fromGitHub
import dev.cl0ud9.krate.domain.model.trackedRef
import dev.cl0ud9.krate.domain.tracked.Forge
import dev.cl0ud9.krate.domain.tracked.RepoRef
import dev.cl0ud9.krate.domain.tracked.parseRepoLink
import dev.cl0ud9.krate.platform.packageinfo.joinAreas
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `reads Codeberg and GitLab links, GitLab groups and pages included`() {
        val codeberg = RepoRef("Freeyourgadget", "Gadgetbridge", Forge.CODEBERG)
        listOf(
            "https://codeberg.org/Freeyourgadget/Gadgetbridge",
            "codeberg.org/Freeyourgadget/Gadgetbridge/releases",
            "https://codeberg.org/Freeyourgadget/Gadgetbridge.git",
        ).forEach { assertEquals(it, codeberg, parseRepoLink(it)) }
        assertEquals(
            RepoRef("AuroraOSS", "AuroraStore", Forge.GITLAB),
            parseRepoLink("gitlab.com/AuroraOSS/AuroraStore"),
        )
        assertEquals(
            RepoRef("group/subgroup", "app", Forge.GITLAB),
            parseRepoLink("https://gitlab.com/group/subgroup/app/-/releases"),
        )
        assertEquals("https://gitlab.com/group/subgroup/app", RepoRef("group/subgroup", "app", Forge.GITLAB).webUrl)
    }

    @Test
    fun `a bare owner and repo is still taken to be on GitHub, and other sites' own pages are turned down`() {
        assertEquals(Forge.GITHUB, parseRepoLink("mihonapp/mihon")?.forge)
        assertNull(parseRepoLink("https://codeberg.org/explore/repos"))
        assertNull(parseRepoLink("https://codeberg.org/Freeyourgadget"))
        assertNull(parseRepoLink("https://gitlab.com/dashboard/projects"))
    }

    @Test
    fun `GitHub apps keep their ids, so ones already tracked stay the same app`() {
        assertEquals("tracked-mihonapp-mihon", trackedIdFor(RepoRef("mihonapp", "mihon")))
        assertEquals("tracked-codeberg-mihonapp-mihon", trackedIdFor(RepoRef("mihonapp", "mihon", Forge.CODEBERG)))
        assertEquals("tracked-gitlab-group_sub-app", trackedIdFor(RepoRef("group/sub", "app", Forge.GITLAB)))
    }

    @Test
    fun `an entry saved before other sites were followed reads back as a GitHub one`() {
        val saved =
            """[{"id": "tracked-o-r", "owner": "o", "repo": "r", "packageName": "p", "displayName": "R",
                "certificateSha256": "abc", "fileShape": "^r$", "addedAtMillis": 1}]"""
        val entry = json.decodeFromString<List<TrackedEntry>>(saved).single()
        assertEquals(Forge.GITHUB, entry.ref.forge)
        val app = entry.toProfile()
        assertTrue(app.fromGitHub)
        assertEquals(RepoRef("o", "r"), app.trackedRef)
    }

    @Test
    fun `a Codeberg entry carries its site through to the app`() {
        val entry =
            TrackedEntry(
                id = "tracked-codeberg-o-r",
                owner = "o",
                repo = "r",
                packageName = "p",
                displayName = "R",
                certificateSha256 = "abc",
                fileShape = "^r$",
                addedAtMillis = 1,
                forge = Forge.CODEBERG.id,
                includePrerelease = true,
            )
        val app = entry.toProfile()
        assertFalse(app.fromGitHub)
        assertEquals(Forge.CODEBERG, app.trackedRef?.forge)
        assertTrue(app.trackedPrerelease)
    }

    @Test
    fun `GitLab releases read their notes, files and test builds from what GitLab gives`() {
        val gitlab =
            """
            [
              {"tag_name": "v3.0.0-beta1", "description": "Try it", "released_at": "2026-10-05T10:00:00Z",
               "assets": {"links": [{"name": "app-arm64-v8a.apk", "url": "https://gitlab.com/x", "direct_asset_url": "https://gitlab.com/d"}]}},
              {"tag_name": "v2.9.0", "description": "", "released_at": "2026-10-01T10:00:00Z", "assets": {"links": []}},
              {"tag_name": "source-2.8", "released_at": "2026-09-01T10:00:00Z"},
              {"tag_name": "v4.0.0", "upcoming_release": true}
            ]
            """.trimIndent()
        val releases = json.decodeFromString<List<GitLabReleaseDto>>(gitlab).map { it.toRelease() }
        assertEquals(listOf(true, false, false, false), releases.map { it.prerelease })
        assertEquals("Try it", releases[0].notes)
        assertNull(releases[1].notes)
        assertEquals("https://gitlab.com/d", releases[0].assets.single().downloadUrl)
    }

    @Test
    fun `new permissions are listed the way people say them`() {
        assertEquals("", joinAreas(emptyList()))
        assertEquals("the camera", joinAreas(listOf("the camera")))
        assertEquals("your location and the camera", joinAreas(listOf("your location", "the camera")))
        assertEquals(
            "your location, the camera and the microphone",
            joinAreas(listOf("your location", "the camera", "the microphone")),
        )
    }
}
