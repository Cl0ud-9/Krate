package dev.cl0ud9.krate.data.catalog

import dev.cl0ud9.krate.domain.model.DeviceProfile
import dev.cl0ud9.krate.domain.model.SetupKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ManifestDtoTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val android12Arm64 = DeviceProfile(31, listOf("arm64-v8a"), krateVersionCode = 7)
    private val android11Arm32 = DeviceProfile(30, listOf("armeabi-v7a"), krateVersionCode = 7)

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
    fun `announcements respect the Krate version range`() {
        val announcements = parse(manifest).announcements

        val old = announcements.mapNotNull { it.toDomain(android12Arm64.copy(krateVersionCode = 6)) }
        val current = announcements.mapNotNull { it.toDomain(android12Arm64) }

        assertEquals(listOf("everyone", "old-versions"), old.map { it.id })
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

    // a step kind added by a later catalog is left out by an older Krate, not shown with nothing to open
    @Test
    fun `an app's setup guide comes through, skipping step kinds this Krate doesn't know`() {
        val guided =
            """{"schemaVersion":2,"apps":[{"id":"guided","displayName":"G","packageName":"p",
            "supportStatus":"SUPPORTED","installationMode":"UPDATE","guide":{
              "setup":[
                {"kind":"ACCESSIBILITY","title":"Turn it on","detail":"Why","service":"p/.Service"},
                {"kind":"SOMETHING_NEW","title":"Later"},
                {"kind":"BATTERY","title":"Background","optional":true}],
              "settingsWhere":"Settings","tips":["One"],"backup":"Export","restore":"Import"}}]}"""

        val guide =
            parse(guided)
                .apps
                .single()
                .toDomain(android12Arm64)!!
                .guide!!

        assertEquals(listOf(SetupKind.ACCESSIBILITY, SetupKind.BATTERY), guide.setup.map { it.kind })
        assertEquals("p/.Service", guide.setup.first().service)
        assertEquals(true, guide.setup.last().optional)
        assertEquals("Export", guide.backup)
        assertNull(
            parse(manifest)
                .apps
                .single()
                .toDomain(android12Arm64)!!
                .guide,
        )
    }

    // an empty guide is no guide, so App Details doesn't show an empty setup card
    @Test
    fun `an empty guide is treated as none`() {
        val empty =
            """{"schemaVersion":2,"apps":[{"id":"bare","displayName":"B","packageName":"p",
            "supportStatus":"SUPPORTED","installationMode":"UPDATE",
            "guide":{"setup":[{"kind":"FUTURE","title":"x"}]}}]}"""

        assertNull(
            parse(empty)
                .apps
                .single()
                .toDomain(android12Arm64)!!
                .guide,
        )
    }

    // the way to an app's backup box comes through whole, and an incomplete one is dropped rather than half followed
    @Test
    fun `an automatic backup path comes through only when it's complete`() {
        fun guideWith(autoBackup: String) =
            parse(
                """{"schemaVersion":2,"apps":[{"id":"a","displayName":"A","packageName":"p",
                "supportStatus":"SUPPORTED","installationMode":"UPDATE",
                "guide":{"backup":"Export","autoBackup":$autoBackup}}]}""",
            ).apps
                .single()
                .toDomain(android12Arm64)!!
                .guide!!

        val complete =
            guideWith(
                """{"path":[{"description":"Profile"},{"text":"Backup","optional":true}],""" +
                    """"close":"Cancel","apply":"Import"}""",
            ).autoBackup!!
        assertEquals(listOf(null, "Backup"), complete.path.map { it.text })
        assertEquals("Profile", complete.path.first().description)
        assertEquals("Import", complete.apply)
        assertEquals(listOf(false, true), complete.path.map { it.optional })

        assertNull(guideWith("""{"path":[{"text":"Backup"}],"close":"Cancel"}""").autoBackup)
        assertNull(guideWith("""{"path":[{}],"close":"Cancel","apply":"Import"}""").autoBackup)
    }

    private fun parse(text: String) = json.decodeFromString<ManifestDto>(text)

    private val manifest =
        """
        {
          "schemaVersion": 2,
          "apps": [{
            "id": "example-player", "displayName": "Example Player", "packageName": "com.example.player",
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
            {"id": "old-versions", "title": "t", "message": "m", "maxKrateVersionCode": 6}
          ]
        }
        """.trimIndent()
}
