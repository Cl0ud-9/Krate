package dev.cl0ud9.manager.data.catalog

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.ArtifactInfo
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.model.SupportStatus
import kotlinx.serialization.Serializable

// mirrors the real output of catalog/scripts/generate_manifest.py, section 9 of the spec
@Serializable
data class ManifestDto(
    val schemaVersion: Int,
    val apps: List<ManifestAppDto>,
)

@Serializable
data class ManifestAppDto(
    val id: String,
    val displayName: String,
    val packageName: String,
    val supportStatus: String,
    val installationMode: String,
    val dependencyIds: List<String> = emptyList(),
    val latestVersionName: String? = null,
    val downloadUrl: String,
    val sha256: String,
    val certificateSha256: String,
    val releaseNotes: String? = null,
    val enabled: Boolean = true,
)

fun ManifestAppDto.toDomain(): AppProfile =
    AppProfile(
        id = id,
        displayName = displayName,
        packageName = packageName,
        supportStatus =
            runCatching {
                SupportStatus.valueOf(supportStatus)
            }.getOrDefault(SupportStatus.TEMPORARILY_UNAVAILABLE),
        installationMode =
            runCatching {
                InstallationMode.valueOf(
                    installationMode,
                )
            }.getOrDefault(InstallationMode.UPDATE),
        dependencyIds = dependencyIds,
        latestVersionName = latestVersionName,
        releaseNotes = releaseNotes,
        enabled = enabled,
        artifact = ArtifactInfo(downloadUrl = downloadUrl, sha256 = sha256, certificateSha256 = certificateSha256),
    )
