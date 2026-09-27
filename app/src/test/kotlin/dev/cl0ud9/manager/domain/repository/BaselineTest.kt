package dev.cl0ud9.manager.domain.repository

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.ArtifactInfo
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.model.SupportStatus
import dev.cl0ud9.manager.domain.model.latestArtifact
import dev.cl0ud9.manager.platform.packageinfo.InstalledVersion
import dev.cl0ud9.manager.platform.packageinfo.isUpdateAvailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineTest {
    private val installed = InstalledVersion("20.40.45", 1)

    @Test
    fun `a newer build of the same version is an update`() {
        val app = app(build("20.40.45", "b2"), build("20.40.45", "b1"), build("20.37.48", "a1"))

        assertTrue(isUpdateAvailable(installed, app, Baseline("20.40.45", "b1")))
    }

    @Test
    fun `the same build is not an update`() {
        val app = app(build("20.40.45", "b2"), build("20.37.48", "a1"))

        assertFalse(isUpdateAvailable(installed, app, Baseline("20.40.45", "b2")))
    }

    @Test
    fun `a build that was pruned from the catalog counts as older`() {
        val app = app(build("20.40.45", "b3"), build("20.37.48", "a1"))

        assertTrue(isUpdateAvailable(installed, app, Baseline("20.40.45", "b1")))
    }

    @Test
    fun `an older version selected on purpose is not newer than the baseline`() {
        val older = build("20.37.48", "a1")
        val app = app(build("20.40.45", "b2"), older)

        assertFalse(older.isNewerThan(Baseline("20.40.45", "b2"), app.artifacts))
    }

    // records from 0.1.5 have no build id: a privately built app is rebuilt whenever its patches
    // change, so the record can't be trusted to be the current build; a public app has one build
    // per version, so the record simply is that build
    // the version history is patches releases: a newer patches release is the update even when it
    // had to target an older app version
    @Test
    fun `a newer patches release is an update even on an older app version`() {
        val app = app(build("20.37.48", "p3"), build("20.40.45", "p2"))

        assertTrue(isUpdateAvailable(installed, app, Baseline("20.40.45", "p2")))
    }

    @Test
    fun `a record without a build id is older only for privately built apps`() {
        val private = app(build("20.40.45", "b2", requiresAuth = true))
        val public = app(build("20.40.45", "v20.40.45"))

        assertTrue(isUpdateAvailable(installed, private, Baseline("20.40.45")))
        assertFalse(isUpdateAvailable(installed, public, Baseline("20.40.45")))
    }

    // no record (data cleared, or installed before records existed): the installed build is taken to
    // be the newest one already published when it was installed, not assumed to be out of date
    @Test
    fun `without a record, an install after the latest build was published counts as that build`() {
        val app =
            app(
                build("20.40.45", "p2", requiresAuth = true, publishedAt = 2_000),
                build("20.37.48", "p1", requiresAuth = true, publishedAt = 1_000),
            )

        assertFalse(isUpdateAvailable(InstalledVersion("20.40.45", 1, lastUpdateTimeMillis = 3_000), app, null))
    }

    @Test
    fun `without a record, a newer build published after the install is an update`() {
        val app =
            app(
                build("20.40.45", "p3", requiresAuth = true, publishedAt = 4_000),
                build("20.40.45", "p2", requiresAuth = true, publishedAt = 2_000),
            )

        assertTrue(isUpdateAvailable(InstalledVersion("20.40.45", 1, lastUpdateTimeMillis = 3_000), app, null))
    }

    @Test
    fun `a withdrawn build is never offered as the latest`() {
        val app =
            app(build("20.40.45", "b2").copy(withdrawn = true), build("20.37.48", "a1"))

        assertEquals("a1", app.latestArtifact?.buildId)
        assertFalse(isUpdateAvailable(InstalledVersion("20.37.48", 1), app, Baseline("20.37.48", "a1")))
    }

    // MicroG RE's own "hide icon" toggle installs a build the catalog never published - with several
    // versions retained, the guess is the build that was current when that one appeared, not the
    // oldest retained one (which would invent an update that doesn't exist)
    @Test
    fun `an unrecognized install is compared against the build current when it appeared`() {
        val diverged = InstalledVersion("7.2.1-dev.2", 9, lastUpdateTimeMillis = 3_000)
        val notMoved = app(build("7.1.1", "v7.1.1", publishedAt = 2_000), build("7.1.0", "v7.1.0", publishedAt = 1_000))
        val moved = app(build("7.1.2", "v7.1.2", publishedAt = 4_000), *notMoved.artifacts.toTypedArray())

        assertFalse(isUpdateAvailable(diverged, notMoved, recordedBaseline = null))
        assertTrue(isUpdateAvailable(diverged, moved, recordedBaseline = null))
    }

    private fun build(
        version: String,
        buildId: String,
        requiresAuth: Boolean = false,
        publishedAt: Long? = null,
    ) = ArtifactInfo(
        versionName = version,
        downloadUrl = "https://example.test/$buildId.apk",
        sha256 = "sha",
        certificateSha256 = "cert",
        requiresAuth = requiresAuth,
        buildId = buildId,
        publishedAtMillis = publishedAt,
    )

    private fun app(vararg artifacts: ArtifactInfo) =
        AppProfile(
            id = "app",
            displayName = "App",
            packageName = "dev.cl0ud9.app",
            supportStatus = SupportStatus.SUPPORTED,
            installationMode = InstallationMode.UPDATE,
            dependencyIds = emptyList(),
            releaseNotes = null,
            enabled = true,
            artifacts = artifacts.toList(),
        )
}
