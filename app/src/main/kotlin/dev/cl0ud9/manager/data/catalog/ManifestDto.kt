package dev.cl0ud9.manager.data.catalog

import dev.cl0ud9.manager.domain.model.Announcement
import dev.cl0ud9.manager.domain.model.AnnouncementSeverity
import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.ArtifactInfo
import dev.cl0ud9.manager.domain.model.DeviceProfile
import dev.cl0ud9.manager.domain.model.InstallationMode
import dev.cl0ud9.manager.domain.model.SupportStatus
import kotlinx.serialization.Serializable
import java.time.Instant

// mirrors the real output of catalog/scripts/generate_manifest.py, section 9 of the spec. Every
// field added in schema 2 has a default, so a schema 1 manifest (an old cache) still parses
@Serializable
data class ManifestDto(
    val schemaVersion: Int,
    val apps: List<ManifestAppDto>,
    val announcements: List<ManifestAnnouncementDto> = emptyList(),
)

@Serializable
data class ManifestArtifactDto(
    val versionName: String,
    val downloadUrl: String,
    val sha256: String,
    val certificateSha256: String,
    val requiresAuth: Boolean = false,
    val patchesVersionName: String? = null,
    val versionCode: Long? = null,
    val buildId: String? = null,
    val minSdk: Int? = null,
    val maxSdk: Int? = null,
    val abis: List<String> = emptyList(),
    val label: String? = null,
    val notes: String? = null,
    val releaseNotes: String? = null,
    val publishedAt: String? = null,
    val withdrawn: Boolean = false,
    val withdrawnReason: String? = null,
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

@Serializable
data class ManifestAnnouncementDto(
    val id: String,
    val severity: String = "INFO",
    val title: String,
    val message: String,
    val appIds: List<String> = emptyList(),
    val actionAppId: String? = null,
    val expiresAt: String? = null,
    val dismissible: Boolean = true,
    val minManagerVersionCode: Long? = null,
    val maxManagerVersionCode: Long? = null,
)

// a build this device can't run (a Material You build on Android 11, an arm64-only build on a
// 32-bit phone) is dropped here, so nothing downstream ever offers it
fun ManifestArtifactDto.fitsDevice(device: DeviceProfile): Boolean =
    (minSdk == null || device.sdkInt >= minSdk) &&
        (maxSdk == null || device.sdkInt <= maxSdk) &&
        (abis.isEmpty() || abis.any { it in device.supportedAbis })

fun ManifestArtifactDto.toDomain(): ArtifactInfo =
    ArtifactInfo(
        versionName = versionName,
        downloadUrl = downloadUrl,
        sha256 = sha256,
        certificateSha256 = certificateSha256,
        requiresAuth = requiresAuth,
        patchesVersionName = patchesVersionName,
        versionCode = versionCode,
        buildId = buildId,
        label = label,
        note = notes,
        releaseNotes = releaseNotes,
        publishedAtMillis = publishedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
        withdrawn = withdrawn,
        withdrawnReason = withdrawnReason,
    )

// null for an installation mode this build doesn't know - installing it the wrong way (an in-place
// update where the catalog asked for something else) is worse than not listing the app at all
fun ManifestAppDto.toDomain(device: DeviceProfile): AppProfile? {
    val mode = runCatching { InstallationMode.valueOf(installationMode) }.getOrNull() ?: return null
    return AppProfile(
        id = id,
        displayName = displayName,
        packageName = packageName,
        supportStatus =
            runCatching {
                SupportStatus.valueOf(supportStatus)
            }.getOrDefault(SupportStatus.TEMPORARILY_UNAVAILABLE),
        installationMode = mode,
        dependencyIds = dependencyIds,
        releaseNotes = releaseNotes,
        enabled = enabled,
        artifacts = artifacts.filter { it.fitsDevice(device) }.map { it.toDomain() },
    )
}

// null when this manager version is outside the announcement's range - a notice like "update the
// manager to keep getting Videoapp builds" only makes sense to managers that are actually too old
fun ManifestAnnouncementDto.toDomain(device: DeviceProfile): Announcement? {
    val tooOld = minManagerVersionCode != null && device.managerVersionCode < minManagerVersionCode
    val tooNew = maxManagerVersionCode != null && device.managerVersionCode > maxManagerVersionCode
    if (tooOld || tooNew) return null
    return Announcement(
        id = id,
        severity = runCatching { AnnouncementSeverity.valueOf(severity) }.getOrDefault(AnnouncementSeverity.INFO),
        title = title,
        message = message,
        appIds = appIds,
        actionAppId = actionAppId,
        expiresAtMillis = expiresAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
        dismissible = dismissible,
    )
}
