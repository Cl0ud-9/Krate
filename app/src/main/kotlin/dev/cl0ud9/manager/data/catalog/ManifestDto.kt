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
data class ManifestArtifactDto(
    val versionName: String,
    val downloadUrl: String,
    val sha256: String,
    val certificateSha256: String,
    val requiresAuth: Boolean = false,
    val patchesVersionName: String? = null,
)

@Serializable
data class ManifestAppDto(
    val id: String,
    val displayName: String,
    val packageName: String,
    val supportStatus: String,
    val installationMode: String,
    val dependencyIds: List<String> = emptyList(),
    // newest first - generate_manifest.py retains a bounded number of past versions per app (see
    // catalog-metadata.json's retainVersions) so a broken newest build still leaves older ones
    // installable, rather than only ever publishing the single latest artifact
    val artifacts: List<ManifestArtifactDto> = emptyList(),
    val releaseNotes: String? = null,
    val enabled: Boolean = true,
)

fun ManifestArtifactDto.toDomain(): ArtifactInfo =
    ArtifactInfo(
        versionName = versionName,
        downloadUrl = downloadUrl,
        sha256 = sha256,
        certificateSha256 = certificateSha256,
        requiresAuth = requiresAuth,
        patchesVersionName = patchesVersionName,
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
        releaseNotes = releaseNotes,
        enabled = enabled,
        artifacts = artifacts.map { it.toDomain() },
    )
