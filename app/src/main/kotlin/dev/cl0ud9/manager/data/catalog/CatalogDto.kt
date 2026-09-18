package dev.cl0ud9.manager.data.catalog

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.model.SupportStatus
import kotlinx.serialization.Serializable

// bootstrap-only shape for the bundled seed asset, no artifact data - see ManifestDto for the real schema
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
    val releaseNotes: String? = null,
    val enabled: Boolean = true,
)

// seed entries never have real download data, so there is nothing to put in artifacts - a real
// manifest fetch (RemoteCatalogRepository's primary path) replaces this with the real thing before
// the user is ever likely to look for a specific version, this is only the offline-with-no-cache
// first-launch fallback
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
        releaseNotes = releaseNotes,
        enabled = enabled,
        artifacts = emptyList(),
    )
