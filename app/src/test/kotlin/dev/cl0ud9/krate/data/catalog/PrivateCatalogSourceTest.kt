package dev.cl0ud9.krate.data.catalog

import com.google.crypto.tink.subtle.Ed25519Sign
import dev.cl0ud9.krate.data.auth.GitHubCredentialStore
import dev.cl0ud9.krate.domain.model.InviteStatus
import dev.cl0ud9.krate.security.manifest.ManifestVerifier
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

// a revoked token must end the invite, while being offline or a server hiccup must not
class PrivateCatalogSourceTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val server = MockWebServer()
    private val keyPair = Ed25519Sign.KeyPair.newKeyPair()
    private val oldCatalog = "old catalog".toByteArray()
    private val newCatalog = """{"apps":[{"id":"one"},{"id":"two"}]}""".toByteArray()
    private lateinit var cacheFile: File

    @Before
    fun setUp() {
        server.start()
        cacheFile = File(tempFolder.root, "private-catalog.json").apply { writeBytes(oldCatalog) }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a verified catalog replaces the cached one`() {
        enqueueRelease(signature = Ed25519Sign(keyPair.privateKey).sign(newCatalog))
        val source = source()

        source.refresh()

        assertArrayEquals(newCatalog, cacheFile.readBytes())
        assertEquals(InviteStatus.Open(appCount = 2), source.status.value)
    }

    @Test
    fun `a cached catalog counts as open before the first check`() {
        cacheFile.writeBytes(newCatalog)

        assertEquals(InviteStatus.Open(appCount = 2), source().status.value)
    }

    @Test
    fun `a bad signature keeps the cached catalog`() {
        enqueueRelease(signature = Ed25519Sign(keyPair.privateKey).sign("something else".toByteArray()))

        source().refresh()

        assertArrayEquals(oldCatalog, cacheFile.readBytes())
    }

    @Test
    fun `a revoked token drops the cached catalog`() {
        server.enqueue(MockResponse().setResponseCode(401))
        val source = source()

        source.refresh()

        assertFalse(cacheFile.exists())
        assertEquals(InviteStatus.Rejected, source.status.value)
    }

    @Test
    fun `losing access to the repo drops the cached catalog`() {
        server.enqueue(MockResponse().setResponseCode(404))

        source().refresh()

        assertFalse(cacheFile.exists())
    }

    @Test
    fun `a server error keeps the cached catalog`() {
        server.enqueue(MockResponse().setResponseCode(503))
        val source = source()

        source.refresh()

        assertArrayEquals(oldCatalog, cacheFile.readBytes())
        assertEquals(InviteStatus.Unreachable, source.status.value)
    }

    @Test
    fun `being offline keeps the cached catalog`() {
        val url = server.url("/release").toString()
        server.shutdown()

        source(releaseUrl = url).refresh()

        assertArrayEquals(oldCatalog, cacheFile.readBytes())
    }

    @Test
    fun `clearing the token hides and drops the cached catalog`() {
        val source = source(token = null)

        assertNull(source.cached())
        source.refresh()

        assertFalse(cacheFile.exists())
        assertTrue(server.requestCount == 0)
        assertEquals(InviteStatus.NoToken, source.status.value)
    }

    private fun source(
        token: String? = "token",
        releaseUrl: String = server.url("/release").toString(),
    ) = PrivateCatalogSource(
        credentialStore = FakeCredentialStore(token),
        httpClient = OkHttpClient(),
        cacheFile = cacheFile,
        verifier = ManifestVerifier(Base64.getEncoder().encodeToString(keyPair.publicKey)),
        releaseUrl = releaseUrl,
    )

    private fun enqueueRelease(signature: ByteArray) {
        val manifestUrl = server.url("/assets/manifest")
        val signatureUrl = server.url("/assets/signature")
        server.enqueue(
            MockResponse().setBody(
                """{"assets":[{"name":"manifest.json","url":"$manifestUrl"},""" +
                    """{"name":"manifest.json.sig","url":"$signatureUrl"}]}""",
            ),
        )
        server.enqueue(MockResponse().setBody(Buffer().write(newCatalog)))
        server.enqueue(MockResponse().setBody(Buffer().write(signature)))
    }

    private class FakeCredentialStore(
        private var token: String?,
    ) : GitHubCredentialStore {
        override fun getToken() = token

        override fun setToken(token: String) {
            this.token = token
        }

        override fun clearToken() {
            token = null
        }
    }
}
