package dev.cl0ud9.manager.domain.model

// download/verification data for the latest artifact, section 9 of the spec
// null for local seed/demo entries that have nothing real to download yet
// requiresAuth is true only for artifacts hosted as a private/draft GitHub release asset (currently
// just Videoapp Patched, kept off public releases) - downloadUrl is then a
// api.github.com/repos/.../releases/assets/{id} URL rather than a plain browser_download_url, and
// needs a bearer token the download engine reads from GitHubCredentialStore
// patchesVersionName is the version of the *tool* that built this artifact (e.g. the Patched
// patches bundle), distinct from the app's own latestVersionName (e.g. the Videoapp version that
// tool patched) - null for artifacts with no such intermediate build tool
data class ArtifactInfo(
    val downloadUrl: String,
    val sha256: String,
    val certificateSha256: String,
    val requiresAuth: Boolean = false,
    val patchesVersionName: String? = null,
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

// an app only belongs in front-of-user surfaces (Apps/Home/Updates) when the catalog's own
// `enabled` kill switch is on, and - for an artifact hosted as a private release asset, currently
// just Videoapp Patched - only once a GitHub token is actually present. Without a token there is
// nothing it could do (its download would just fail with a 401), so it should not be offered as an
// option in the first place rather than shown and then broken. Dependency resolution deliberately
// does not use this: a dependency must still resolve against the full catalog regardless of whether
// it would itself be visible if browsed directly
fun AppProfile.isVisible(hasGitHubToken: Boolean): Boolean =
    enabled && (artifact?.requiresAuth != true || hasGitHubToken)
