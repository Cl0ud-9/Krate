package dev.cl0ud9.manager.domain.installer

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.InstallStatus
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.model.SupportStatus
import dev.cl0ud9.manager.platform.rollback.RollbackStore
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
            latestVersionName = "1.0.0",
            releaseNotes = null,
            enabled = true,
            artifact = null,
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
    }
}
