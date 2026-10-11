package dev.cl0ud9.krate.data.catalog

import dev.cl0ud9.krate.domain.model.DeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// one release can ship a file for each processor; a phone is only ever offered one of them, the preferred that fits
class ManifestBuildsTest {
    private fun artifact(
        version: String,
        abi: String,
    ) = ManifestArtifactDto(
        versionName = version,
        downloadUrl = "https://x/$version-$abi.apk",
        sha256 = "sha-$abi",
        certificateSha256 = "cert",
        buildId = version,
        abis = listOf(abi),
    )

    private fun app(openActivity: String? = null) =
        ManifestAppDto(
            id = "sample",
            displayName = "Sample",
            packageName = "pkg.sample",
            supportStatus = "SUPPORTED",
            installationMode = "UPDATE",
            // the catalog lists the preferred processor first within a release
            artifacts =
                listOf(
                    artifact("2.0", "arm64-v8a"),
                    artifact("2.0", "armeabi-v7a"),
                    artifact("1.0", "arm64-v8a"),
                ),
            openActivity = openActivity,
        )

    @Test
    fun `a phone that runs both processors is offered the preferred file once per version`() {
        val device =
            DeviceProfile(sdkInt = 34, supportedAbis = listOf("arm64-v8a", "armeabi-v7a"), krateVersionCode = 1)

        val built = app().toDomain(device)!!.artifacts

        assertEquals(
            listOf("2.0" to "arm64-v8a", "1.0" to "arm64-v8a"),
            built.map { it.versionName to it.sha256.removePrefix("sha-") },
        )
    }

    @Test
    fun `a 32-bit phone is offered the file that fits it, and nothing it can't run`() {
        val device = DeviceProfile(sdkInt = 34, supportedAbis = listOf("armeabi-v7a"), krateVersionCode = 1)

        val built = app().toDomain(device)!!.artifacts

        assertEquals(listOf("2.0" to "armeabi-v7a"), built.map { it.versionName to it.sha256.removePrefix("sha-") })
    }

    @Test
    fun `the screen an app opens to when it has no icon comes through, and a blank one doesn't`() {
        val device = DeviceProfile(sdkInt = 34, supportedAbis = listOf("arm64-v8a"), krateVersionCode = 1)

        assertEquals("a.b.Settings", app(openActivity = " a.b.Settings ").toDomain(device)!!.openActivity)
        assertNull(app(openActivity = "  ").toDomain(device)!!.openActivity)
        assertNull(app().toDomain(device)!!.openActivity)
    }
}
