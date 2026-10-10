package dev.cl0ud9.krate.data.tracked

import dev.cl0ud9.krate.domain.model.isTracked
import dev.cl0ud9.krate.domain.tracked.RepoRef
import dev.cl0ud9.krate.domain.tracked.nameShape
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackedEntriesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val arm64Phone = listOf("arm64-v8a", "armeabi-v7a", "armeabi")

    // the shape of GitHub's own answer, trimmed to the fields Krate reads
    private val releasesJson =
        """
        [
          {"tag_name": "v2.1.0-beta", "prerelease": true, "draft": false, "published_at": "2026-10-05T10:00:00Z",
           "assets": [{"name": "app-2.1.0-beta-arm64-v8a.apk", "browser_download_url": "https://github.com/o/r/releases/download/v2.1.0-beta/a.apk", "size": 10}]},
          {"tag_name": "v2.0.0", "body": "Faster sync", "prerelease": false, "draft": false, "published_at": "2026-10-01T10:00:00Z",
           "assets": [
             {"name": "app-2.0.0-armeabi-v7a.apk", "browser_download_url": "https://github.com/o/r/releases/download/v2.0.0/b.apk", "size": 9},
             {"name": "app-2.0.0-arm64-v8a.apk", "browser_download_url": "https://github.com/o/r/releases/download/v2.0.0/c.apk", "size": 11,
              "digest": "sha256:abc123"}]},
          {"tag_name": "v1.9.0", "prerelease": false, "draft": false, "published_at": "2026-09-01T10:00:00Z",
           "assets": [{"name": "source.zip", "browser_download_url": "https://github.com/o/r/archive/v1.9.0.zip", "size": 5}]},
          {"tag_name": "v1.8.0", "prerelease": false, "draft": false, "published_at": "2026-08-01T10:00:00Z",
           "assets": [{"name": "app-1.8.0-arm64-v8a.apk", "browser_download_url": "https://github.com/o/r/releases/download/v1.8.0/d.apk", "size": 8}]}
        ]
        """.trimIndent()

    private val releases = json.decodeFromString<List<ReleaseDto>>(releasesJson).map { it.toRelease() }

    @Test
    fun `reads a release the way GitHub sends it, digest included`() {
        val stable = releases[1]
        assertEquals("v2.0.0", stable.tag)
        assertEquals("Faster sync", stable.notes)
        assertEquals(1_790_848_800_000L, stable.publishedAtMillis)
        assertEquals("abc123", stable.assets.single { it.name.contains("arm64") }.sha256)
        assertNull(stable.assets.single { it.name.contains("armeabi") }.sha256)
    }

    @Test
    fun `keeps the file shaped like the chosen one from each stable release, newest first`() {
        val builds = buildsFrom(releases, nameShape("app-2.0.0-arm64-v8a.apk"), includePrerelease = false, arm64Phone)
        assertEquals(listOf("v2.0.0", "v1.8.0"), builds.map { it.tag })
        assertEquals("app-2.0.0-arm64-v8a.apk", builds.first().fileName)
        assertEquals("abc123", builds.first().sha256)
    }

    @Test
    fun `pre-releases only count when asked for`() {
        val builds = buildsFrom(releases, nameShape("app-2.0.0-arm64-v8a.apk"), includePrerelease = true, arm64Phone)
        assertEquals("v2.1.0-beta", builds.first().tag)
    }

    @Test
    fun `a tracked app looks like any other app to the rest of Krate, with its signer pinned`() {
        val entry =
            TrackedEntry(
                id = trackedIdFor(RepoRef("Some-Owner", "Cool.App")),
                owner = "Some-Owner",
                repo = "Cool.App",
                packageName = "com.example.cool",
                displayName = "Cool App",
                certificateSha256 = "feed",
                fileShape = nameShape("app-2.0.0-arm64-v8a.apk"),
                addedAtMillis = 0L,
                builds = buildsFrom(releases, nameShape("app-2.0.0-arm64-v8a.apk"), false, arm64Phone),
            )
        val app = entry.toProfile()
        assertEquals("tracked-some-owner-cool.app", app.id)
        assertTrue(app.isTracked)
        assertEquals("Some-Owner/Cool.App", app.trackedRepo)
        assertEquals(listOf("2.0.0", "1.8.0"), app.artifacts.map { it.versionName })
        assertTrue(app.artifacts.all { it.certificateSha256 == "feed" && !it.requiresAuth })
        assertEquals("v2.0.0", app.artifacts.first().buildId)
        // no digest from GitHub for that build: checked by its signature alone
        assertEquals("", app.artifacts[1].sha256)
    }

    @Test
    fun `tracked ids never look like a catalog id`() {
        assertTrue(trackedIdFor(RepoRef("A", "B")).startsWith("tracked-"))
        assertFalse(trackedIdFor(RepoRef("a b", "c/d")).contains(" "))
    }
}
