package dev.cl0ud9.manager.platform.packageinfo

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.latestArtifact
import dev.cl0ud9.manager.domain.repository.Baseline
import dev.cl0ud9.manager.domain.repository.effectiveBaseline
import dev.cl0ud9.manager.domain.repository.isNewerThan

// the installed version of a package on this device, section 13 + 42.19 of the spec use this to
// decide Install vs Update wording and to compute real pending-update counts
data class InstalledVersion(
    val versionName: String?,
    val versionCode: Long,
    val lastUpdateTimeMillis: Long = 0L,
)

interface InstalledPackageReader {
    fun installedVersion(packageName: String): InstalledVersion?
}

// true when the catalog's latest is newer than the effective baseline (see
// ManagerBaselineStore.effectiveBaseline) - the build the manager itself last installed, or its
// best guess when it never has. Deliberately NOT compared against the live-installed version
// directly: a package can end up diverged from the catalog entirely outside this app (MicroG RE's
// own in-app "hide icon" toggle installs its own beta build, for example), and by explicit product
// decision that divergence alone should never suppress a real update - nor should it manufacture
// one that doesn't exist. If the catalog hasn't moved past the baseline, this stays false (Open +
// Redownload is the right UI there, not a manufactured "Update"); once the catalog genuinely passes
// the baseline, this flips true regardless of what the live-installed version's own number is
fun isUpdateAvailable(
    installed: InstalledVersion?,
    app: AppProfile,
    recordedBaseline: Baseline?,
): Boolean {
    val latest = app.latestArtifact
    if (installed == null || latest == null) return false
    val baseline = effectiveBaseline(recordedBaseline, app, installed)
    return baseline == null || latest.isNewerThan(baseline, app.artifacts)
}
