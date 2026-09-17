package dev.cl0ud9.manager.domain.model

// download/verification data for the latest artifact, section 9 of the spec
// null for local seed/demo entries that have nothing real to download yet
// requiresAuth is true only for artifacts hosted as a private/draft GitHub release asset (currently
// just Videoapp Patched, kept off public releases) - downloadUrl is then a
// api.github.com/repos/.../releases/assets/{id} URL rather than a plain browser_download_url, and
// needs a bearer token the download engine reads from GitHubCredentialStore
data class ArtifactInfo(
    val downloadUrl: String,
    val sha256: String,
    val certificateSha256: String,
    val requiresAuth: Boolean = false,
)

// curated catalog entry, section 7 of the spec
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
    val artifact: ArtifactInfo?,
)
