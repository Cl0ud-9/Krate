package dev.cl0ud9.krate.ui.details

import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.ArtifactInfo
import dev.cl0ud9.krate.domain.model.DownloadStatus
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.model.SupportStatus
import dev.cl0ud9.krate.domain.repository.Baseline
import dev.cl0ud9.krate.domain.repository.forTrack
import dev.cl0ud9.krate.platform.packageinfo.InstalledVersion
import dev.cl0ud9.krate.platform.packageinfo.isUpdateAvailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDetailsUiStateTest {
    private val v230 = build("2.3.0", 230)
    private val v2210 = build("2.2.10", 2210)
    private val v229 = build("2.2.9", 229)
    private val app = app(listOf(v230, v2210, v229))

    @Test
    fun `a version between the installed one and the latest is an update, not a rollback`() {
        val state = state(app, installed = v229, selected = v2210)

        assertFalse(state.isRollback)
        assertEquals("Update", actionLabelFor(state))
    }

    @Test
    fun `a version older than the installed one is a rollback that uninstalls first`() {
        val state = state(app, installed = v230, selected = v229)

        assertTrue(state.isRollback)
        assertTrue(state.installsFromScratch)
        assertEquals("Roll back", actionLabelFor(state))
    }

    @Test
    fun `the installed version itself is a reinstall`() {
        val state = state(app, installed = v230, selected = v230)

        assertFalse(state.isRollback)
        assertEquals("Reinstall", actionLabelFor(state))
    }

    @Test
    fun `another theme of the installed version is a switch, not a rollback or a reinstall`() {
        val modern = build("20.40.45", 1, label = "Material You", buildId = "modern")
        val classic = build("20.40.45", 1, label = "Classic theme", buildId = "classic")
        val themed = app(listOf(modern, classic))

        val state = state(themed, installed = modern, selected = classic)

        assertTrue(state.isSwitch)
        assertFalse(state.isRollback)
        assertFalse(state.installsFromScratch)
        assertEquals("Switch to this build", actionLabelFor(state))
    }

    @Test
    fun `the latest build is never a rollback, even when a newer one was installed outside Krate`() {
        val state =
            state(app, installed = v230, selected = v230)
                .copy(installed = InstalledVersion("2.4.0-beta", 240))

        assertFalse(state.isRollback)
    }

    @Test
    fun `each theme is its own track for updates`() {
        val modernNew = build("20.41.0", 2, label = "Material You", buildId = "modern-new")
        val modern = build("20.40.45", 1, label = "Material You", buildId = "modern")
        val classic = build("20.40.45", 1, label = "Classic theme", buildId = "classic")
        val themed = app(listOf(modernNew, modern, classic))
        val classicInstalled = InstalledVersion("20.40.45", 1)

        assertFalse(isUpdateAvailable(classicInstalled, themed, Baseline("20.40.45", "classic", "Classic theme")))
        assertTrue(isUpdateAvailable(InstalledVersion("20.40.45", 1), themed, Baseline("20.40.45", "modern")))
        assertEquals(listOf(classic), themed.forTrack(Baseline("20.40.45", "classic")).artifacts)
        val classicNew = build("20.41.0", 2, label = "Classic theme", buildId = "classic-new")
        val updated = app(listOf(modernNew, classicNew, modern, classic))
        assertTrue(isUpdateAvailable(classicInstalled, updated, Baseline("20.40.45", "classic", "Classic theme")))
    }

    @Test
    fun `an in-place update can fall back to installing from scratch, a clean-install app can't`() {
        assertFalse(state(app, installed = v229, selected = v230).installsFromScratch)
        val clean = app.copy(installationMode = InstallationMode.CLEAN_INSTALL)
        assertTrue(state(clean, installed = v229, selected = v230).installsFromScratch)
    }

    // one tap does the whole thing where Android can install over what's there, so the button names the result
    @Test
    fun `the button says what one tap will do`() {
        assertEquals("Update", downloadLabelFor(state(app, installed = v229, selected = v230)))
        assertEquals("Reinstall", downloadLabelFor(state(app, installed = v230, selected = v230)))
        val notInstalled = state(app, installed = v230, selected = v230).copy(installed = null, recordedBaseline = null)
        assertEquals("Install", downloadLabelFor(notInstalled))
        assertTrue(notInstalled.installsInPlace)
    }

    // a rollback erases data first, so the first tap only downloads and the rollback stays its own step
    @Test
    fun `steps that erase data first only download on the first tap`() {
        val rollback = state(app, installed = v230, selected = v229)
        assertFalse(rollback.installsInPlace)
        assertEquals("Download", downloadLabelFor(rollback))
        assertEquals("Roll back", actionLabelFor(rollback))

        val clean =
            state(app.copy(installationMode = InstallationMode.CLEAN_INSTALL), installed = v229, selected = v230)
        assertEquals("Download", downloadLabelFor(clean))
    }

    @Test
    fun `a public release asset links to its release page, a private one doesn't`() {
        val public = build("1.0", 1).copy(downloadUrl = "https://github.com/owner/repo/releases/download/v1.0/app.apk")
        assertEquals("https://github.com/owner/repo/releases/tag/v1.0", public.releasePageUrl())
        assertNull(public.copy(requiresAuth = true).releasePageUrl())
        assertNull(public.copy(downloadUrl = "https://example.com/app.apk").releasePageUrl())
    }

    private fun build(
        version: String,
        code: Long,
        label: String? = null,
        buildId: String = "v$version",
    ) = ArtifactInfo(
        versionName = version,
        downloadUrl = "https://example.com/$buildId.apk",
        sha256 = "",
        certificateSha256 = "",
        versionCode = code,
        buildId = buildId,
        label = label,
    )

    private fun app(artifacts: List<ArtifactInfo>) =
        AppProfile(
            id = "app",
            displayName = "App",
            packageName = "com.example.app",
            supportStatus = SupportStatus.SUPPORTED,
            installationMode = InstallationMode.UPDATE,
            dependencyIds = emptyList(),
            releaseNotes = null,
            enabled = true,
            artifacts = artifacts,
        )

    private fun state(
        app: AppProfile,
        installed: ArtifactInfo,
        selected: ArtifactInfo,
    ) = AppDetailsUiState(
        app = app,
        installed = InstalledVersion(installed.versionName, installed.versionCode ?: 0L),
        recordedBaseline = Baseline(installed.versionName, installed.buildId),
        dependencies = emptyList(),
        downloadStatus = DownloadStatus.Idle,
        installStatus = InstallStatus.Idle,
        selectedArtifact = selected,
    )
}
