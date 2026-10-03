package dev.cl0ud9.krate.domain.installer

import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.InstallStatus
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.model.SupportStatus
import dev.cl0ud9.krate.platform.rollback.RollbackStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// section 17, 18, 21, 42.13 of the spec: preserve -> uninstall -> install, restore on failure
class CleanInstallOrchestratorTest {
    private val app =
        AppProfile(
            id = "sample",
            displayName = "Sample",
            packageName = "dev.cl0ud9.sample",
            supportStatus = SupportStatus.SUPPORTED,
            installationMode = InstallationMode.CLEAN_INSTALL,
            dependencyIds = emptyList(),
            releaseNotes = null,
            enabled = true,
            artifacts = emptyList(),
        )
    private val newApk = File("new.apk")
    private val rollbackApk = File("rollback.apk")

    @Test
    fun `successful clean install preserves, uninstalls, then installs the new apk`() =
        runBlocking {
            val engine =
                FakeInstallationEngine(
                    uninstallResult = flowOf(InstallStatus.Success),
                    installResults = mutableListOf(flowOf(InstallStatus.Installing, InstallStatus.Success)),
                )
            val rollbackStore = FakeRollbackStore(captureResult = true, rollbackFile = rollbackApk)
            val orchestrator = CleanInstallOrchestrator(engine, rollbackStore)

            val statuses = orchestrator.cleanInstall(app, newApk).toList()

            assertEquals(
                listOf(
                    InstallStatus.PreparingRollback,
                    InstallStatus.Uninstalling,
                    InstallStatus.Installing,
                    InstallStatus.Success,
                ),
                statuses,
            )
            assertEquals(listOf(newApk), engine.installedFiles)
            assertEquals(1, rollbackStore.captureCalls)
            // the old apk's copy isn't kept once the new build is installed
            assertEquals(1, rollbackStore.discarded)
        }

    @Test
    fun `uninstall failure stops before ever installing`() =
        runBlocking {
            val engine =
                FakeInstallationEngine(
                    uninstallResult = flowOf(InstallStatus.Failed("uninstall blocked")),
                    installResults = mutableListOf(),
                )
            val rollbackStore = FakeRollbackStore(captureResult = true, rollbackFile = rollbackApk)
            val orchestrator = CleanInstallOrchestrator(engine, rollbackStore)

            val statuses = orchestrator.cleanInstall(app, newApk).toList()

            val last = statuses.last()
            assertTrue(last is InstallStatus.Failed)
            assertEquals("uninstall blocked", (last as InstallStatus.Failed).reason)
            assertTrue(engine.installedFiles.isEmpty())
        }

    @Test
    fun `install failure restores the preserved apk and reports rolledBack true`() =
        runBlocking {
            val engine =
                FakeInstallationEngine(
                    uninstallResult = flowOf(InstallStatus.Success),
                    installResults =
                        mutableListOf(
                            flowOf(InstallStatus.Failed("corrupt apk")),
                            flowOf(InstallStatus.Success),
                        ),
                )
            val rollbackStore = FakeRollbackStore(captureResult = true, rollbackFile = rollbackApk)
            val orchestrator = CleanInstallOrchestrator(engine, rollbackStore)

            val statuses = orchestrator.cleanInstall(app, newApk).toList()

            val last = statuses.last()
            assertTrue(last is InstallStatus.Failed)
            assertEquals("corrupt apk", (last as InstallStatus.Failed).reason)
            assertTrue(last.rolledBack)
            assertEquals(listOf(newApk, rollbackApk), engine.installedFiles)
            // the old app is back on, so its copy isn't needed any more
            assertEquals(1, rollbackStore.discarded)
        }

    @Test
    fun `when even the rollback fails the copy is kept, the last one left`() =
        runBlocking {
            val engine =
                FakeInstallationEngine(
                    uninstallResult = flowOf(InstallStatus.Success),
                    installResults =
                        mutableListOf(
                            flowOf(InstallStatus.Failed("corrupt apk")),
                            flowOf(InstallStatus.Failed("still broken")),
                        ),
                )
            val rollbackStore = FakeRollbackStore(captureResult = true, rollbackFile = rollbackApk)

            val last = CleanInstallOrchestrator(engine, rollbackStore).cleanInstall(app, newApk).toList().last()

            assertTrue(last is InstallStatus.Failed && !last.rolledBack)
            assertEquals(0, rollbackStore.discarded)
        }

    @Test
    fun `install failure without a captured rollback reports rolledBack false`() =
        runBlocking {
            val engine =
                FakeInstallationEngine(
                    uninstallResult = flowOf(InstallStatus.Success),
                    installResults = mutableListOf(flowOf(InstallStatus.Failed("corrupt apk"))),
                )
            val rollbackStore = FakeRollbackStore(captureResult = false, rollbackFile = null)
            val orchestrator = CleanInstallOrchestrator(engine, rollbackStore)

            val statuses = orchestrator.cleanInstall(app, newApk).toList()

            val last = statuses.last()
            assertTrue(last is InstallStatus.Failed)
            assertTrue(!(last as InstallStatus.Failed).rolledBack)
            assertEquals(listOf(newApk), engine.installedFiles)
        }

    private class FakeInstallationEngine(
        private val uninstallResult: Flow<InstallStatus>,
        private val installResults: MutableList<Flow<InstallStatus>>,
    ) : InstallationEngine {
        val installedFiles = mutableListOf<File>()

        override fun install(
            app: AppProfile,
            apkFile: File,
        ): Flow<InstallStatus> {
            installedFiles.add(apkFile)
            return installResults.removeAt(0)
        }

        override fun uninstall(packageName: String): Flow<InstallStatus> = uninstallResult
    }

    private class FakeRollbackStore(
        private val captureResult: Boolean,
        private val rollbackFile: File?,
    ) : RollbackStore {
        var captureCalls = 0
            private set

        override fun capture(packageName: String): Boolean {
            captureCalls++
            return captureResult
        }

        override fun rollbackFile(packageName: String): File? = rollbackFile

        var discarded = 0
            private set

        override fun discard(packageName: String) {
            discarded++
        }

        override fun clearAll(): Long = 0L
    }
}
