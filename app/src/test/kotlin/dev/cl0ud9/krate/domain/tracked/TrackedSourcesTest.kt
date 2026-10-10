package dev.cl0ud9.krate.domain.tracked

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackedSourcesTest {
    private val arm64Phone = listOf("arm64-v8a", "armeabi-v7a", "armeabi")

    private fun assets(vararg names: String) = names.map { ReleaseAsset(it, "https://example.com/$it", 1L) }

    @Test
    fun `reads every shape of repository link people copy`() {
        val expected = RepoRef("Clash-Projects", "LastWave-Native")
        listOf(
            "https://github.com/Clash-Projects/LastWave-Native",
            "https://github.com/Clash-Projects/LastWave-Native/",
            "http://www.github.com/Clash-Projects/LastWave-Native",
            "github.com/Clash-Projects/LastWave-Native",
            "https://github.com/Clash-Projects/LastWave-Native/releases/tag/v4.2.4",
            "https://github.com/Clash-Projects/LastWave-Native.git",
            "https://github.com/Clash-Projects/LastWave-Native?tab=readme-ov-file",
            "  Clash-Projects/LastWave-Native  ",
        ).forEach { assertEquals(it, expected, parseRepoLink(it)) }
    }

    @Test
    fun `turns down links that are not a repository`() {
        listOf(
            "",
            "lastwave",
            "https://example.com/Clash-Projects/LastWave-Native",
            "https://github.com/Clash-Projects",
            "https://github.com/orgs/Clash-Projects",
            "https://github.com/settings/tokens",
        ).forEach { assertNull(it, parseRepoLink(it)) }
    }

    @Test
    fun `a tag's leading v is not part of the version`() {
        assertEquals("4.2.4", versionFromTag("v4.2.4"))
        assertEquals("7.2.1", versionFromTag("7.2.1"))
        assertEquals("release-12", versionFromTag("release-12"))
    }

    @Test
    fun `reads the processor family from a file name`() {
        assertEquals("arm64-v8a", abiOf("app-arm64-v8a-release.apk"))
        assertEquals("arm64-v8a", abiOf("mihon-arm64-v8a-v0.20.4.apk"))
        assertEquals("armeabi-v7a", abiOf("app-armeabi-v7a.apk"))
        assertEquals("x86_64", abiOf("app-x86_64.apk"))
        assertEquals("x86", abiOf("app-x86.apk"))
        assertNull(abiOf("app-universal-release.apk"))
        assertNull(abiOf("microg-7.2.1.apk"))
    }

    @Test
    fun `puts the phone's own processor first, then builds for every phone, and leaves out ones it can't run`() {
        val picked =
            apkCandidates(
                assets(
                    "app-x86_64.apk",
                    "app-universal.apk",
                    "app-armeabi-v7a.apk",
                    "app-arm64-v8a.apk",
                    "app-x86.apk",
                    "notes.txt",
                ),
                arm64Phone,
            ).map { it.name }
        assertEquals(listOf("app-arm64-v8a.apk", "app-universal.apk", "app-armeabi-v7a.apk"), picked)
    }

    @Test
    fun `debug builds only count when a release has nothing else`() {
        assertEquals(
            listOf(
                "app-release.apk",
            ),
            apkCandidates(assets("app-debug.apk", "app-release.apk"), arm64Phone).map {
                it.name
            },
        )
        assertEquals(listOf("app-debug.apk"), apkCandidates(assets("app-debug.apk"), arm64Phone).map { it.name })
    }

    @Test
    fun `a file name's shape matches the same kind of file in a later release`() {
        val shape = Regex(nameShape("mihon-arm64-v8a-v0.20.4.apk"))
        assertTrue(shape.matches("mihon-arm64-v8a-v0.21.0.apk"))
        assertFalse(shape.matches("mihon-armeabi-v7a-v0.21.0.apk"))
        val microg = Regex(nameShape("microg-7.2.1-icon-arm64-v8a.apk"))
        assertTrue(microg.matches("microg-7.3.0-icon-arm64-v8a.apk"))
        assertFalse(microg.matches("microg-7.3.0-noicon-arm64-v8a.apk"))
        assertFalse(Regex(nameShape("app-x86.apk")).matches("app-x86_64.apk"))
    }

    @Test
    fun `picks the file shaped like the chosen one, and the best fit after a rename`() {
        val shape = nameShape("microg-7.2.1-icon-arm64-v8a.apk")
        val next = assets("microg-7.3.0-noicon-arm64-v8a.apk", "microg-7.3.0-icon-arm64-v8a.apk", "microg-7.3.0.apk")
        assertEquals("microg-7.3.0-icon-arm64-v8a.apk", pickAsset(next, shape, arm64Phone)?.name)
        val renamed = assets("microg-8.0.0-universal.apk", "microg-8.0.0-full-arm64-v8a.apk")
        assertEquals("microg-8.0.0-full-arm64-v8a.apk", pickAsset(renamed, shape, arm64Phone)?.name)
        assertNull(pickAsset(assets("source.zip"), shape, arm64Phone))
    }
}
