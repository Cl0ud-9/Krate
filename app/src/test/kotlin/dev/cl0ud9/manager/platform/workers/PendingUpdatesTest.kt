package dev.cl0ud9.manager.platform.workers

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.model.SupportStatus
import dev.cl0ud9.manager.platform.packageinfo.InstalledPackageReader
import dev.cl0ud9.manager.platform.packageinfo.InstalledVersion
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingUpdatesTest {
    @Test
    fun `counts only apps whose installed version differs from the catalog's latest`() {
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

        val count = pendingUpdateCount(listOf(upToDate, pending, notInstalled), reader)

        assertEquals(1, count)
    }

    @Test
    fun `zero pending apps counts as zero`() {
        val app = profile("app", latestVersionName = "1.0.0")
        val reader = FakeInstalledPackageReader(mapOf(app.packageName to InstalledVersion("1.0.0", 1)))

        assertEquals(0, pendingUpdateCount(listOf(app), reader))
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
            latestVersionName = latestVersionName,
            releaseNotes = null,
            enabled = true,
            artifact = null,
        )

    private class FakeInstalledPackageReader(
        private val installed: Map<String, InstalledVersion>,
    ) : InstalledPackageReader {
        override fun installedVersion(packageName: String): InstalledVersion? = installed[packageName]
    }
}
