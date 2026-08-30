package dev.cl0ud9.manager.data.catalog

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.model.SupportStatus
import kotlinx.serialization.Serializable

// mirrors the central manifest shape from section 9 of the spec, trimmed to what phase 1 needs
@Serializable
data class CatalogDto(
    val schemaVersion: Int,
    val apps: List<AppProfileDto>,
)

@Serializable
data class AppProfileDto(
    val id: String,
    val displayName: String,
    val packageName: String,
    val supportStatus: String,
    val installationMode: String,
    val dependencyIds: List<String> = emptyList(),
    val latestVersionName: String? = null,
    val releaseNotes: String? = null,
    val enabled: Boolean = true,
)

fun AppProfileDto.toDomain(): AppProfile =
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
    )
