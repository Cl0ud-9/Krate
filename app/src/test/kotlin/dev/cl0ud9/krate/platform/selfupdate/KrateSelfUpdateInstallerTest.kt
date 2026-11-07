package dev.cl0ud9.krate.platform.selfupdate

import dev.cl0ud9.krate.domain.installer.InstallationEngine
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.InstallStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private const val WAIT_MS = 10_000L
private val PLAY_PROTECT =
    InstallStatus.Failed(
        "Google Play Protect stopped this install.",
        blockedByPlayProtect = true,
    )
private val BROKEN = InstallStatus.Failed("The package appears to be invalid.")

// Krate's own update: a second go at the same release (after pausing Play Protect) installs the file it already has
class KrateSelfUpdateInstallerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val server = MockWebServer()
    private val apk = "a whole update".toByteArray()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private class FakeEngine(
        var result: InstallStatus,
    ) : InstallationEngine {
        val installed = mutableListOf<ByteArray>()

        override fun install(
            app: AppProfile,
            apkFile: File,
        ): Flow<InstallStatus> {
            installed += apkFile.readBytes()
            return flowOf(result)
        }

        override fun uninstall(packageName: String): Flow<InstallStatus> = flowOf(InstallStatus.Success)
    }

    private fun installer(engine: InstallationEngine) =
        KrateSelfUpdateInstaller(tempFolder.root, "dev.cl0ud9.krate", {}, engine, OkHttpClient())

    private fun serve(times: Int) = repeat(times) { server.enqueue(MockResponse().setBody(okio.Buffer().write(apk))) }

    // starts the update and waits for it to stop, however it ends
    private fun KrateSelfUpdateInstaller.run(url: String): SelfUpdateState? =
        runBlocking {
            start(url)
            withTimeout(WAIT_MS) { join() }
            state.value
        }

    @Test
    fun `trying again after Play Protect installs the same file without downloading it again`() {
        serve(times = 2)
        val engine = FakeEngine(PLAY_PROTECT)
        val installer = installer(engine)
        val url = server.url("/v0.6.7/krate-0.6.7.apk").toString()

        installer.run(url)
        engine.result = InstallStatus.Success
        installer.run(url)

        assertEquals("downloads", 1, server.requestCount)
        assertTrue(engine.installed.all { it.contentEquals(apk) })
    }

    @Test
    fun `a newer release is downloaded even when an older one is still here`() {
        serve(times = 2)
        val engine = FakeEngine(PLAY_PROTECT)
        val installer = installer(engine)

        installer.run(server.url("/v0.6.7/krate-0.6.7.apk").toString())
        installer.run(server.url("/v0.6.8/krate-0.6.8.apk").toString())

        assertEquals("downloads", 2, server.requestCount)
    }

    @Test
    fun `a file Android turned down is downloaded again on the next try`() {
        serve(times = 2)
        val engine = FakeEngine(BROKEN)
        val installer = installer(engine)
        val url = server.url("/v0.6.7/krate-0.6.7.apk").toString()

        installer.run(url)
        installer.run(url)

        assertEquals("downloads", 2, server.requestCount)
    }

    @Test
    fun `a cut-off download is never installed or kept`() {
        server.enqueue(
            MockResponse()
                .setBody(okio.Buffer().write(ByteArray(256 * 1024)))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        serve(times = 1)
        val engine = FakeEngine(InstallStatus.Success)
        val installer = installer(engine)
        val url = server.url("/v0.6.7/krate-0.6.7.apk").toString()

        assertTrue(installer.run(url) is SelfUpdateState.DownloadFailed)
        assertEquals("installs after the cut-off download", 0, engine.installed.size)
        installer.run(url)

        assertEquals("downloads", 2, server.requestCount)
        assertTrue(engine.installed.single().contentEquals(apk))
    }

    @Test
    fun `clearing the cache removes a downloaded update, so the next try downloads it again`() {
        serve(times = 2)
        val engine = FakeEngine(PLAY_PROTECT)
        val installer = installer(engine)
        val url = server.url("/v0.6.7/krate-0.6.7.apk").toString()

        installer.run(url)
        assertEquals("bytes freed", apk.size.toLong(), installer.clearDownload())
        installer.run(url)

        assertEquals("downloads", 2, server.requestCount)
    }
}
