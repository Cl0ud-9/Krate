package dev.cl0ud9.manager.data.catalog

import dev.cl0ud9.manager.domain.model.DeviceProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ManifestDtoTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val android12Arm64 = DeviceProfile(31, listOf("arm64-v8a"), managerVersionCode = 7)
    private val android11Arm32 = DeviceProfile(30, listOf("armeabi-v7a"), managerVersionCode = 7)

    @Test
    fun `builds are filtered to what the device can run`() {
        val app = parse(manifest).apps.single()

        val modern = app.toDomain(android12Arm64)!!.artifacts.map { it.buildId }
        val legacy = app.toDomain(android11Arm32)!!.artifacts.map { it.buildId }

        assertEquals(listOf("modern", "arm64-only"), modern)
        assertEquals(listOf("legacy"), legacy)
    }

    @Test
    fun `an unknown installation mode drops the app instead of guessing`() {
        val app = parse(manifest.replace("\"UPDATE\"", "\"SOMETHING_NEW\"")).apps.single()

        assertNull(app.toDomain(android12Arm64))
    }

    @Test
    fun `announcements respect the manager version range`() {
        val announcements = parse(manifest).announcements

        val old = announcements.mapNotNull { it.toDomain(android12Arm64.copy(managerVersionCode = 6)) }
        val current = announcements.mapNotNull { it.toDomain(android12Arm64) }

        assertEquals(listOf("everyone", "old-managers"), old.map { it.id })
        assertEquals(listOf("everyone"), current.map { it.id })
    }

    @Test
    fun `a schema 1 manifest still parses`() {
        val v1 = """{"schemaVersion":1,"apps":[{"id":"a","displayName":"A","packageName":"p","supportStatus":"SUPPORTED",
            "installationMode":"UPDATE","artifacts":[{"versionName":"1.0","downloadUrl":"u","sha256":"s",
            "certificateSha256":"c"}]}]}"""

        val app = parse(v1).apps.single().toDomain(android11Arm32)

        assertNotNull(app)
        assertEquals("1.0", app!!.artifacts.single().versionName)
    }

    @Test
    fun `an app's description and highlights come through, and are simply absent from older catalogs`() {
        val described =
            """{"schemaVersion":2,"apps":[{"id":"a","displayName":"A","description":"Does a thing.",
            "highlights":["One","Two"],"packageName":"p","supportStatus":"SUPPORTED","installationMode":"UPDATE"}]}"""

        val app = parse(described).apps.single().toDomain(android12Arm64)!!
        val plain = parse(manifest).apps.single().toDomain(android12Arm64)!!

        assertEquals("Does a thing.", app.description)
        assertEquals(listOf("One", "Two"), app.highlights)
        assertNull(plain.description)
        assertEquals(emptyList<String>(), plain.highlights)
    }

    private fun parse(text: String) = json.decodeFromString<ManifestDto>(text)

    private val manifest =
        """
        {
          "schemaVersion": 2,
          "apps": [{
            "id": "videoapp-patched", "displayName": "Videoapp", "packageName": "app.patched.android.videoapp",
            "supportStatus": "SUPPORTED", "installationMode": "UPDATE",
            "artifacts": [
              {"versionName": "20.40.45", "buildId": "modern", "minSdk": 31, "downloadUrl": "u1", "sha256": "s",
               "certificateSha256": "c"},
              {"versionName": "20.40.45", "buildId": "legacy", "maxSdk": 30, "downloadUrl": "u2", "sha256": "s",
               "certificateSha256": "c"},
              {"versionName": "20.37.48", "buildId": "arm64-only", "abis": ["arm64-v8a"], "downloadUrl": "u3",
               "sha256": "s", "certificateSha256": "c"}
            ]
          }],
          "announcements": [
            {"id": "everyone", "title": "t", "message": "m"},
            {"id": "old-managers", "title": "t", "message": "m", "maxManagerVersionCode": 6}
          ]
        }
        """.trimIndent()
}
