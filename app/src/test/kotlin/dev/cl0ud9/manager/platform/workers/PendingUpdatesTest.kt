package dev.cl0ud9.manager.platform.workers

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.ArtifactInfo
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.model.SupportStatus
import dev.cl0ud9.manager.domain.repository.Baseline
import dev.cl0ud9.manager.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.manager.platform.packageinfo.InstalledVersion
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingUpdatesTest {
    @Test
    fun `counts only apps whose baseline is actually behind the catalog's latest`() {
        val upToDate = profile("up-to-date", latestVersionName = "1.0.0")
        // installed's "1.0.0" needs to be catalog-known (a real retained older artifact) for this
        // to exercise ordinary numeric comparison rather than the unrecognized-build fallback path
        val pending =
            profile("pending", latestVersionName = "2.0.0").copy(
                artifacts =
                    listOf(
                        ArtifactInfo("2.0.0", "https://example.test/pending-2.apk", "sha", "cert"),
                        ArtifactInfo("1.0.0", "https://example.test/pending-1.apk", "sha", "cert"),
                    ),
            )
        val notInstalled = profile("not-installed", latestVersionName = "1.0.0")
        val reader =
            FakeInstalledPackageReader(
                mapOf(
                    upToDate.packageName to InstalledVersion("1.0.0", 1),
                    pending.packageName to InstalledVersion("1.0.0", 1),
                ),
            )

        val count =
            pendingUpdateCount(
                listOf(upToDate, pending, notInstalled),
                reader,
                hasGitHubToken = false,
                baselines = emptyMap(),
            )

        assertEquals(1, count)
    }

    // deliberate policy, confirmed with the user: a build the catalog has never published (MicroG
    // RE's own in-app "hide icon" toggle installs its own beta build, for example) should NOT be
    // treated as pending just for being unrecognized - only once the catalog genuinely publishes
    // something beyond the manager's baseline (here, the fallback guess, since none is recorded)
    @Test
    fun `an unrecognized installed version is not pending while the catalog hasn't moved past the fallback baseline`() {
        val aheadOfCatalog = profile("ahead", latestVersionName = "7.1.1")
        val reader =
            FakeInstalledPackageReader(mapOf(aheadOfCatalog.packageName to InstalledVersion("7.2.1-dev.2", 1)))

        val count = pendingUpdateCount(listOf(aheadOfCatalog), reader, hasGitHubToken = false, baselines = emptyMap())

        assertEquals(0, count)
    }

    // once the catalog genuinely publishes something beyond the fallback baseline, it becomes
    // pending again - regardless of the live-installed build's own (unrecognized) version number
    @Test
    fun `an unrecognized installed version becomes pending once the catalog passes the fallback baseline`() {
        val app =
            profile("ahead-then-caught-up", latestVersionName = "7.1.1").copy(
                artifacts =
                    listOf(
                        ArtifactInfo("7.1.2", "https://example.test/2.apk", "sha", "cert"),
                        ArtifactInfo("7.1.1", "https://example.test/1.apk", "sha", "cert"),
                    ),
            )
        val reader = FakeInstalledPackageReader(mapOf(app.packageName to InstalledVersion("7.2.1-dev.2", 1)))

        val count = pendingUpdateCount(listOf(app), reader, hasGitHubToken = false, baselines = emptyMap())

        assertEquals(1, count)
    }

    // an explicitly recorded baseline always wins over the fallback guess - this is what a real
    // manager-driven install writes, and it must keep working correctly even while the live device
    // has since diverged to something the catalog doesn't recognize
    @Test
    fun `a recorded baseline is used over the live installed version`() {
        val app = profile("recorded", latestVersionName = "1.0.2")
        val reader = FakeInstalledPackageReader(mapOf(app.packageName to InstalledVersion("1.0.0-custom", 1)))

        val notPending =
            pendingUpdateCount(
                listOf(app),
                reader,
                hasGitHubToken = false,
                baselines = mapOf(app.packageName to Baseline("1.0.2")),
            )
        val pending =
            pendingUpdateCount(
                listOf(app),
                reader,
                hasGitHubToken = false,
                baselines = mapOf(app.packageName to Baseline("1.0.1")),
            )

        assertEquals(0, notPending)
        assertEquals(1, pending)
    }

    @Test
    fun `zero pending apps counts as zero`() {
        val app = profile("app", latestVersionName = "1.0.0")
        val reader = FakeInstalledPackageReader(mapOf(app.packageName to InstalledVersion("1.0.0", 1)))

        assertEquals(0, pendingUpdateCount(listOf(app), reader, hasGitHubToken = false, baselines = emptyMap()))
    }

    @Test
    fun `excludes a disabled app even if its installed version differs from latest`() {
        val disabled = profile("disabled", latestVersionName = "2.0.0").copy(enabled = false)
        val reader = FakeInstalledPackageReader(mapOf(disabled.packageName to InstalledVersion("1.0.0", 1)))

        assertEquals(0, pendingUpdateCount(listOf(disabled), reader, hasGitHubToken = false, baselines = emptyMap()))
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
                        // catalog-known older artifact, same reasoning as the "pending" test above -
                        // without this, installed "1.0.0" would hit the unrecognized-build fallback
                        // path instead of exercising ordinary numeric comparison
                        ArtifactInfo(
                            versionName = "1.0.0",
                            downloadUrl = "https://example.test/gated-1.apk",
                            sha256 = "sha",
                            certificateSha256 = "cert",
                            requiresAuth = true,
                        ),
                    ),
            )
        val reader = FakeInstalledPackageReader(mapOf(gated.packageName to InstalledVersion("1.0.0", 1)))

        assertEquals(0, pendingUpdateCount(listOf(gated), reader, hasGitHubToken = false, baselines = emptyMap()))
        assertEquals(1, pendingUpdateCount(listOf(gated), reader, hasGitHubToken = true, baselines = emptyMap()))
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
