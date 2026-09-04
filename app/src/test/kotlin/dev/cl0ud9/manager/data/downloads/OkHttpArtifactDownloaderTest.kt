package dev.cl0ud9.manager.data.downloads

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.ArtifactInfo
import dev.cl0ud9.manager.domain.model.DownloadStatus
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.model.SupportStatus
import dev.cl0ud9.manager.security.apk.ApkArchiveInfo
import dev.cl0ud9.manager.security.apk.ApkArchiveReader
import dev.cl0ud9.manager.security.hash.sha256Hex
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// exercises the real download/verify pipeline against a local http server, no android framework needed
class OkHttpArtifactDownloaderTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val server = MockWebServer()
    private val payload = "fake apk bytes for verification testing".toByteArray()
    private val payloadSha256 = sha256Hex(payload.inputStream())
    private val matchingCertSha256 = "cert-hash-matching-manifest"

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `successful download passes hash, certificate and package checks`() =
        runBlocking {
            enqueuePayloadTwice()
            val downloader = downloaderWithReader(FakeArchiveReader(MATCHING_PACKAGE, matchingCertSha256))

            val statuses = downloader.download(appProfile()).toList()

            val ready = statuses.last()
            assertTrue(ready is DownloadStatus.ReadyToInstall)
            assertTrue(File((ready as DownloadStatus.ReadyToInstall).filePath).exists())
        }

    @Test
    fun `hash mismatch is rejected and the artifact is deleted`() =
        runBlocking {
            enqueuePayloadTwice()
            val downloader = downloaderWithReader(FakeArchiveReader(MATCHING_PACKAGE, matchingCertSha256))
            val app = appProfile()
            val corrupted = app.copy(artifact = app.artifact!!.copy(sha256 = "0000000000000000"))

            val statuses = downloader.download(corrupted).toList()

            val failure = statuses.last()
            assertTrue(failure is DownloadStatus.Failed)
            assertTrue((failure as DownloadStatus.Failed).reason.contains("checksum"))
        }

    @Test
    fun `certificate mismatch is rejected`() =
        runBlocking {
            enqueuePayloadTwice()
            val downloader = downloaderWithReader(FakeArchiveReader(MATCHING_PACKAGE, "wrong-cert-hash"))

            val statuses = downloader.download(appProfile()).toList()

            val failure = statuses.last()
            assertTrue(failure is DownloadStatus.Failed)
            assertTrue((failure as DownloadStatus.Failed).reason.contains("certificate"))
        }

    @Test
    fun `package name mismatch is rejected`() =
        runBlocking {
            enqueuePayloadTwice()
            val downloader = downloaderWithReader(FakeArchiveReader("some.other.package", matchingCertSha256))

            val statuses = downloader.download(appProfile()).toList()

            val failure = statuses.last()
            assertTrue(failure is DownloadStatus.Failed)
            assertTrue((failure as DownloadStatus.Failed).reason.contains("package name"))
        }

    @Test
    fun `missing artifact fails immediately without a network call`() =
        runBlocking {
            val downloader = downloaderWithReader(FakeArchiveReader(MATCHING_PACKAGE, matchingCertSha256))
            val appWithoutArtifact = appProfile().copy(artifact = null)

            val statuses = downloader.download(appWithoutArtifact).toList()

            assertEquals(1, statuses.size)
            assertTrue(statuses.single() is DownloadStatus.Failed)
        }

    // the downloader issues a HEAD request for storage preflight before the real GET, so queue both;
    // the HEAD response must carry no body bytes on the wire or MockWebServer corrupts the reused connection
    private fun enqueuePayloadTwice() {
        server.enqueue(MockResponse().setHeader("Content-Length", payload.size.toString()))
        server.enqueue(MockResponse().setBody(Buffer().write(payload)))
    }

    private fun downloaderWithReader(reader: ApkArchiveReader): OkHttpArtifactDownloader =
        OkHttpArtifactDownloader(downloadsDir = tempFolder.newFolder(), archiveReader = reader)

    private fun appProfile(): AppProfile =
        AppProfile(
            id = "sample-app",
            displayName = "Sample App",
            packageName = MATCHING_PACKAGE,
            supportStatus = SupportStatus.SUPPORTED,
            installationMode = InstallationMode.UPDATE,
            dependencyIds = emptyList(),
            latestVersionName = "1.0.0",
            releaseNotes = null,
            enabled = true,
            artifact =
                ArtifactInfo(
                    downloadUrl = server.url("/sample.apk").toString(),
                    sha256 = payloadSha256,
                    certificateSha256 = matchingCertSha256,
                ),
        )

    private class FakeArchiveReader(
        private val packageName: String,
        private val certificateSha256Hex: String,
    ) : ApkArchiveReader {
        override fun read(apkPath: String): ApkArchiveInfo =
            ApkArchiveInfo(packageName = packageName, certificateSha256Hex = certificateSha256Hex)
    }

    private companion object {
        const val MATCHING_PACKAGE = "dev.cl0ud9.sample"
    }
}
