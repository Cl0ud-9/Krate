package dev.cl0ud9.manager.domain.model

// curated catalog entry, section 7 of the spec
// artifact/workflow/rollback fields land with manifest ingestion in phase 2
data class AppProfile(
    val id: String,
    val displayName: String,
    val packageName: String,
    val supportStatus: SupportStatus,
    val installationMode: InstallationMode,
    val dependencyIds: List<String>,
    val latestVersionName: String?,
    val releaseNotes: String?,
    val enabled: Boolean,
)
