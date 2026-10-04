package dev.cl0ud9.krate.data.catalog

import dev.cl0ud9.krate.domain.model.Announcement
import dev.cl0ud9.krate.domain.model.AnnouncementSeverity
import dev.cl0ud9.krate.domain.model.AppGuide
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.model.ArtifactInfo
import dev.cl0ud9.krate.domain.model.AutoBackup
import dev.cl0ud9.krate.domain.model.DeviceProfile
import dev.cl0ud9.krate.domain.model.InstallationMode
import dev.cl0ud9.krate.domain.model.SetupKind
import dev.cl0ud9.krate.domain.model.SetupStep
import dev.cl0ud9.krate.domain.model.SupportStatus
import dev.cl0ud9.krate.domain.model.UiTarget
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
    val iconPng: String? = null,
    val description: String? = null,
    val highlights: List<String> = emptyList(),
    val guide: ManifestGuideDto? = null,
)

@Serializable
data class ManifestGuideDto(
    val setup: List<ManifestSetupStepDto> = emptyList(),
    val settingsWhere: String? = null,
    val tips: List<String> = emptyList(),
    val backup: String? = null,
    val restore: String? = null,
    val autoBackup: ManifestAutoBackupDto? = null,
)

@Serializable
data class ManifestAutoBackupDto(
    val path: List<ManifestUiTargetDto> = emptyList(),
    val close: String = "",
    val apply: String = "",
)

@Serializable
data class ManifestUiTargetDto(
    val text: String? = null,
    val description: String? = null,
    val optional: Boolean = false,
)

@Serializable
data class ManifestSetupStepDto(
    val kind: String,
    val title: String,
    val detail: String = "",
    val optional: Boolean = false,
    val service: String? = null,
)

// a step of a kind this Krate doesn't know yet is left out rather than shown with nothing to open
fun ManifestGuideDto.toDomain(): AppGuide =
    AppGuide(
        setup =
            setup.mapNotNull { step ->
                runCatching { SetupKind.valueOf(step.kind) }.getOrNull()?.let { kind ->
                    SetupStep(kind, step.title, step.detail, step.optional, step.service)
                }
            },
        settingsWhere = settingsWhere,
        tips = tips,
        backup = backup,
        restore = restore,
        autoBackup = autoBackup?.toDomain(),
    )

// left out unless it's complete: every step names something, and both buttons are there
private fun ManifestAutoBackupDto.toDomain(): AutoBackup? =
    takeIf { spec ->
        spec.path.isNotEmpty() &&
            spec.path.all { it.text != null || it.description != null } &&
            spec.close.isNotBlank() &&
            spec.apply.isNotBlank()
    }?.let { spec ->
        AutoBackup(spec.path.map { UiTarget(it.text, it.description, it.optional) }, spec.close, spec.apply)
    }

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
    val minKrateVersionCode: Long? = null,
    val maxKrateVersionCode: Long? = null,
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
        iconPng = iconPng,
        description = description,
        highlights = highlights,
        guide = guide?.toDomain()?.takeUnless { it.isEmpty },
    )
}

// null when this Krate version is outside the announcement's range - a notice like "update
// Krate to keep getting updates" only makes sense to Krate versions that are actually too old
fun ManifestAnnouncementDto.toDomain(device: DeviceProfile): Announcement? {
    val tooOld = minKrateVersionCode != null && device.krateVersionCode < minKrateVersionCode
    val tooNew = maxKrateVersionCode != null && device.krateVersionCode > maxKrateVersionCode
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
