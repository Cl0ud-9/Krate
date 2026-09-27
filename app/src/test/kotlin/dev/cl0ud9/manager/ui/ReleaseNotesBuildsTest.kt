package dev.cl0ud9.manager.ui

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.ArtifactInfo
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.model.SupportStatus
import dev.cl0ud9.manager.domain.repository.Baseline
import dev.cl0ud9.manager.ui.details.releaseNotesBuilds
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesBuildsTest {
    private fun build(
        version: String,
        withdrawn: Boolean = false,
    ) = ArtifactInfo(version, "https://x/$version.apk", "sha", "cert", withdrawn = withdrawn)

    private val v3 = build("3.0.0")
    private val v2 = build("2.0.0", withdrawn = true)
    private val v1 = build("1.0.0")
    private val app =
        AppProfile(
            "a",
            "A",
            "a.pkg",
            SupportStatus.SUPPORTED,
            InstallationMode.UPDATE,
            emptyList(),
            null,
            true,
            listOf(v3, v2, v1),
        )

    private fun versions(builds: List<ArtifactInfo>) = builds.map { it.versionName }

    @Test
    fun `an update shows every build since the installed one, skipping withdrawn ones`() {
        assertEquals(listOf("3.0.0"), versions(releaseNotesBuilds(app, v3, Baseline("1.0.0"))))
        val older = app.copy(artifacts = listOf(v3, build("2.0.0"), v1))
        assertEquals(
            listOf("3.0.0", "2.0.0"),
            versions(releaseNotesBuilds(older, older.artifacts.first(), Baseline("1.0.0"))),
        )
    }

    @Test
    fun `nothing installed, up to date, or an older pick shows just the selected build`() {
        assertEquals(listOf("3.0.0"), versions(releaseNotesBuilds(app, v3, null)))
        assertEquals(listOf("3.0.0"), versions(releaseNotesBuilds(app, v3, Baseline("3.0.0"))))
        assertEquals(listOf("1.0.0"), versions(releaseNotesBuilds(app, v1, Baseline("3.0.0"))))
        assertEquals(emptyList<String>(), versions(releaseNotesBuilds(app, null, Baseline("1.0.0"))))
    }
}
