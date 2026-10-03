package dev.cl0ud9.krate.ui.details

import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.ArtifactInfo
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.platform.packageinfo.InstalledVersion

// Android refuses an in-place install of a lower versionCode, so a build older than the installed
// one can only go on after uninstalling it (which erases the app's data)
internal fun requiresUninstall(
    installed: InstalledVersion?,
    artifact: ArtifactInfo?,
): Boolean {
    val versionCode = artifact?.versionCode ?: return false
    return installed != null && versionCode < installed.versionCode
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
