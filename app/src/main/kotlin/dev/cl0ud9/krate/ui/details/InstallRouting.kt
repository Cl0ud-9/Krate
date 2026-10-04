package dev.cl0ud9.krate.ui.details

import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.ArtifactInfo
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.platform.packageinfo.InstalledVersion
import dev.cl0ud9.krate.security.hash.hashesMatch

// Android refuses an in-place install of a lower versionCode, or of a build signed with another key than the
// installed copy, so those can only go on after uninstalling it (which erases the app's data)
internal fun requiresUninstall(
    installed: InstalledVersion?,
    artifact: ArtifactInfo?,
): Boolean = isOlderThanInstalled(installed, artifact) || isSignedDifferently(installed, artifact)

internal fun isOlderThanInstalled(
    installed: InstalledVersion?,
    artifact: ArtifactInfo?,
): Boolean {
    val versionCode = artifact?.versionCode ?: return false
    return installed != null && versionCode < installed.versionCode
}

// a copy installed from somewhere else, signed with its own key: no build of Krate's can update it in place
internal fun isSignedDifferently(
    installed: InstalledVersion?,
    artifact: ArtifactInfo?,
): Boolean {
    val installedSigner = installed?.signerSha256 ?: return false
    return artifact != null && !hashesMatch(artifact.certificateSha256, installedSigner)
}

// an install Android can do over what's on the phone (or onto nothing): one tap downloads and installs it. Rollbacks
// and apps that always reinstall clean erase data first, so those keep a separate, deliberate step
internal fun canInstallInPlace(
    app: AppProfile,
    installed: InstalledVersion?,
    artifact: ArtifactInfo?,
): Boolean =
    installed == null ||
        (app.installationMode != InstallationMode.CLEAN_INSTALL && !requiresUninstall(installed, artifact))
