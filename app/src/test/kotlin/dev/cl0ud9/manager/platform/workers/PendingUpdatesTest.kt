package dev.cl0ud9.manager.platform.workers

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.ArtifactInfo
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.model.SupportStatus
import dev.cl0ud9.manager.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.manager.platform.packageinfo.InstalledVersion
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingUpdatesTest {
    @Test
    fun `counts only apps whose catalog latest is actually newer than what's installed`() {
        val upToDate = profile("up-to-date", latestVersionName = "1.0.0")
        val pending = profile("pending", latestVersionName = "2.0.0")
        val notInstalled = profile("not-installed", latestVersionName = "1.0.0")
        val reader =
            FakeInstalledPackageReader(
                mapOf(
                    upToDate.packageName to InstalledVersion("1.0.0", 1),
                    pending.packageName to InstalledVersion("1.0.0", 1),
                ),
            )

        val count = pendingUpdateCount(listOf(upToDate, pending, notInstalled), reader, hasGitHubToken = false)

        assertEquals(1, count)
    }

    // the real bug this covers: a package can end up ahead of the catalog entirely outside this
    // app (MicroG RE's own in-app "hide icon" toggle installs its own beta build) - this must not
    // count as a pending update, since Update would actually downgrade it back to the catalog's
    // older release
    @Test
    fun `an installed version newer than the catalog is not a pending update`() {
        val aheadOfCatalog = profile("ahead", latestVersionName = "7.1.1")
        val reader =
            FakeInstalledPackageReader(mapOf(aheadOfCatalog.packageName to InstalledVersion("7.2.1-dev.2", 1)))

        assertEquals(0, pendingUpdateCount(listOf(aheadOfCatalog), reader, hasGitHubToken = false))
    }

    @Test
    fun `zero pending apps counts as zero`() {
        val app = profile("app", latestVersionName = "1.0.0")
        val reader = FakeInstalledPackageReader(mapOf(app.packageName to InstalledVersion("1.0.0", 1)))

        assertEquals(0, pendingUpdateCount(listOf(app), reader, hasGitHubToken = false))
    }

    @Test
    fun `excludes a disabled app even if its installed version differs from latest`() {
        val disabled = profile("disabled", latestVersionName = "2.0.0").copy(enabled = false)
        val reader = FakeInstalledPackageReader(mapOf(disabled.packageName to InstalledVersion("1.0.0", 1)))

        assertEquals(0, pendingUpdateCount(listOf(disabled), reader, hasGitHubToken = false))
    }

    @Test
    fun `excludes a requiresAuth app without a token even if pending`() {
        val gated =
            profile("gated", latestVersionName = "2.0.0").copy(
                artifacts =
                    listOf(
                        ArtifactInfo(
                            versionName = "2.0.0",
                            downloadUrl = "https://example.test/gated.apk",
                            sha256 = "sha",
                            certificateSha256 = "cert",
                            requiresAuth = true,
                        ),
                    ),
            )
        val reader = FakeInstalledPackageReader(mapOf(gated.packageName to InstalledVersion("1.0.0", 1)))

        assertEquals(0, pendingUpdateCount(listOf(gated), reader, hasGitHubToken = false))
        assertEquals(1, pendingUpdateCount(listOf(gated), reader, hasGitHubToken = true))
    }

    private fun profile(
        id: String,
        latestVersionName: String,
    ): AppProfile =
        AppProfile(
            id = id,
            displayName = id,
            packageName = "dev.cl0ud9.$id",
            supportStatus = SupportStatus.SUPPORTED,
            installationMode = InstallationMode.UPDATE,
            dependencyIds = emptyList(),
            releaseNotes = null,
            enabled = true,
            artifacts =
                listOf(
                    ArtifactInfo(
                        versionName = latestVersionName,
                        downloadUrl = "https://example.test/$id.apk",
                        sha256 = "sha",
                        certificateSha256 = "cert",
                    ),
                ),
        )

    private class FakeInstalledPackageReader(
        private val installed: Map<String, InstalledVersion>,
    ) : InstalledPackageReader {
        override fun installedVersion(packageName: String): InstalledVersion? = installed[packageName]
    }
}
