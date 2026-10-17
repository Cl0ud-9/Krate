package dev.cl0ud9.krate.ui

import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.model.SupportStatus
import dev.cl0ud9.krate.platform.backup.AutoBackupState
import dev.cl0ud9.krate.platform.backup.BackupMode
import dev.cl0ud9.krate.ui.details.helperReportText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperReportTest {
    private val app =
        AppProfile(
            id = "player",
            displayName = "Player",
            packageName = "com.example.player",
            supportStatus = SupportStatus.SUPPORTED,
            installationMode = InstallationMode.UPDATE,
            dependencyIds = emptyList(),
            releaseNotes = null,
            enabled = true,
            artifacts = emptyList(),
        )
    private val failure =
        AutoBackupState.Failed(
            "com.example.player",
            BackupMode.APPLY,
            "Couldn't find \"Import\" in the app.",
        )

    @Test
    fun `the report names the app, what it was doing, why it stopped and the device`() {
        val text = helperReportText(app, failure, "Device: Nothing AIN065\n", "10-15 00:05 helper start")

        assertTrue(text.contains("Player (com.example.player)"))
        assertTrue(text.contains("Doing: apply"))
        assertTrue(text.contains("Reason shown: Couldn't find \"Import\" in the app."))
        assertTrue(text.contains("Device: Nothing AIN065"))
        assertTrue(text.contains("helper start"))
    }

    @Test
    fun `an empty log leaves its heading out`() {
        assertFalse(helperReportText(app, failure, "Device: x\n", "").contains("own log"))
    }
}
