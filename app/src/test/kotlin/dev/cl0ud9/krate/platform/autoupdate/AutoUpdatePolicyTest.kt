package dev.cl0ud9.krate.platform.autoupdate

import android.content.pm.PackageInstaller
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.ArtifactInfo
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.model.SupportStatus
import dev.cl0ud9.krate.domain.repository.AutoUpdateChoices
import dev.cl0ud9.krate.domain.repository.newerThanPicked
import dev.cl0ud9.krate.platform.packageinfo.InstalledVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUpdatePolicyTest {
    private val released = 1_000_000_000_000L
    private val dayLater = released + AUTO_UPDATE_DELAY_MILLIS
    private val app = app("reader")

    @Test
    fun `an update Krate owns goes on once a day has passed since release`() {
        val policy = policy()

        assertTrue(policy.willUpdate(app))
        assertFalse(policy.isDue(app, dayLater - 1))
        assertTrue(policy.isDue(app, dayLater))
        assertEquals(dayLater, policy.dueAt(app))
    }

    @Test
    fun `the user's choices keep an app on manual`() {
        assertFalse(policy(choices = AutoUpdateChoices(manual = setOf("reader"))).willUpdate(app))
        assertFalse(policy(choices = AutoUpdateChoices(skipped = mapOf("reader" to "b2"))).willUpdate(app))
        // a skip only covers the build it was made for
        assertTrue(policy(choices = AutoUpdateChoices(skipped = mapOf("reader" to "b1"))).willUpdate(app))
    }

    @Test
    fun `an app another store installed, or one not installed at all, is left alone`() {
        assertFalse(policy(owned = false).willUpdate(app))
        assertFalse(policy(installed = emptyMap()).willUpdate(app))
    }

    @Test
    fun `nothing that erases data goes on by itself`() {
        val clean = app.copy(installationMode = InstallationMode.CLEAN_INSTALL)
        // the installed build is newer than the catalog's, so only an uninstall could put this one on
        val downgrade = policy(installed = mapOf(app.packageName to InstalledVersion("3.0", 30)))

        assertFalse(policy().willUpdate(clean))
        assertFalse(downgrade.willUpdate(app))
    }

    @Test
    fun `a missing dependency waits for the user, and dependencies go first`() {
        val helper = app("helper")
        val needsHelper = app("player").copy(dependencyIds = listOf("helper"))
        val catalog = listOf(needsHelper, helper)

        val without = policy(catalog = catalog, installed = installed(needsHelper))
        val with = policy(catalog = catalog, installed = installed(needsHelper, helper))

        assertFalse(without.willUpdate(needsHelper))
        assertTrue(with.willUpdate(needsHelper))
        assertEquals(listOf("helper", "player"), with.inInstallOrder(listOf(needsHelper, helper)).map { it.id })
    }

    @Test
    fun `a build that keeps failing is left for the user, and one without a release date never goes on`() {
        val undated = app.copy(artifacts = app.artifacts.map { it.copy(publishedAtMillis = null) })

        assertFalse(policy(tries = MAX_AUTO_UPDATE_TRIES).willUpdate(app))
        assertTrue(policy(tries = MAX_AUTO_UPDATE_TRIES - 1).willUpdate(app))
        assertFalse(policy().willUpdate(undated))
    }

    @Test
    fun `an update still not on a day after Krate first found it due is overdue`() {
        assertFalse(isOverdue(dueSinceMillis = released, nowMillis = released + AUTO_UPDATE_DELAY_MILLIS))
        assertTrue(isOverdue(dueSinceMillis = released, nowMillis = released + AUTO_UPDATE_DELAY_MILLIS + 1))
    }

    @Test
    fun `install results map to what Krate does next`() {
        assertEquals(AutoUpdateOutcome.UPDATED, autoUpdateOutcome(PackageInstaller.STATUS_SUCCESS, 0))
        assertEquals(AutoUpdateOutcome.NEEDS_YOU, autoUpdateOutcome(PackageInstaller.STATUS_PENDING_USER_ACTION, 0))
        // Android 14's wait ran out while the app was in use: not a failure
        assertEquals(AutoUpdateOutcome.NOT_NOW, autoUpdateOutcome(8, 0))
        assertEquals(AutoUpdateOutcome.GIVE_UP, autoUpdateOutcome(PackageInstaller.STATUS_FAILURE_CONFLICT, 0))
        assertEquals(AutoUpdateOutcome.TRY_AGAIN, autoUpdateOutcome(PackageInstaller.STATUS_FAILURE, 0))
        assertEquals(
            AutoUpdateOutcome.GIVE_UP,
            autoUpdateOutcome(PackageInstaller.STATUS_FAILURE, MAX_AUTO_UPDATE_TRIES - 1),
        )
    }

    @Test
    fun `a rollback skips the build it stepped back from, an update skips nothing`() {
        val newest = app.artifacts.first()
        val older = app.artifacts.last()

        assertEquals("b2", app.newerThanPicked(older)?.buildId)
        assertNull(app.newerThanPicked(newest))
    }

    @Test
    fun `notifications name the apps`() {
        assertEquals("Krate updated Mihon while you weren't using it.", updatedText(listOf("Mihon")))
        assertEquals(
            "Krate updated Mihon, LastWave while you weren't using them.",
            updatedText(listOf("Mihon", "LastWave")),
        )
    }

    private fun policy(
        catalog: List<AppProfile> = listOf(app),
        choices: AutoUpdateChoices = AutoUpdateChoices(),
        tries: Int = 0,
        owned: Boolean = true,
        installed: Map<String, InstalledVersion> = installed(app),
    ) = AutoUpdatePolicy(catalog, choices, { _, _ -> tries }) { profile ->
        AutoUpdateFacts(installed[profile.packageName], owned)
    }

    private fun installed(vararg apps: AppProfile) = apps.associate { it.packageName to InstalledVersion("1.0", 10) }

    private fun app(id: String) =
        AppProfile(
            id = id,
            displayName = id,
            packageName = "com.example.$id",
            supportStatus = SupportStatus.SUPPORTED,
            installationMode = InstallationMode.UPDATE,
            dependencyIds = emptyList(),
            releaseNotes = null,
            enabled = true,
            artifacts =
                listOf(
                    ArtifactInfo(
                        "2.0",
                        "u2",
                        "s",
                        "c",
                        versionCode = 20,
                        buildId = "b2",
                        publishedAtMillis = released,
                    ),
                    ArtifactInfo(
                        "1.0",
                        "u1",
                        "s",
                        "c",
                        versionCode = 10,
                        buildId = "b1",
                        publishedAtMillis = released - AUTO_UPDATE_DELAY_MILLIS,
                    ),
                ),
        )
}
