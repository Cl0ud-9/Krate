package dev.cl0ud9.manager.domain.model

// one downloadable build of an app, section 9 of the spec - AppProfile.artifacts holds every
// version currently retained (newest first), not just the latest, so a broken newest build still
// leaves older versions installable
// requiresAuth is true only for artifacts hosted as a private GitHub release asset (the
// Patched-style apps, kept off public releases) - downloadUrl is then a
// api.github.com/repos/.../releases/assets/{id} URL rather than a plain browser_download_url, and
// needs a bearer token the download engine reads from GitHubCredentialStore
// patchesVersionName is the version of the *tool* that built this artifact (e.g. the Patched
// patches bundle), distinct from versionName (the app's own version, e.g. Videoapp's) - null for
// artifacts with no such intermediate build tool
data class ArtifactInfo(
    val versionName: String,
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
    val releaseNotes: String?,
    val enabled: Boolean,
    // newest first; empty for a local seed/demo entry with nothing real to download yet
    val artifacts: List<ArtifactInfo>,
)

val AppProfile.latestArtifact: ArtifactInfo?
    get() = artifacts.firstOrNull()

// kept as a computed property (not a stored field) so there is exactly one source of truth for
// "the app's newest version" - every call site that used to read a separately-tracked
// latestVersionName field now reads this instead, and it can never drift out of sync with artifacts
val AppProfile.latestVersionName: String?
    get() = latestArtifact?.versionName

// an app only belongs in front-of-user surfaces (Apps/Home/Updates) when the catalog's own
// `enabled` kill switch is on, and - for an artifact hosted as a private release asset, currently
// the Patched-style apps - only once a GitHub token is actually present. Without a token there is
// nothing it could do (its download would just fail with a 401), so it should not be offered as an
// option in the first place rather than shown and then broken. Dependency resolution deliberately
// does not use this: a dependency must still resolve against the full catalog regardless of whether
// it would itself be visible if browsed directly
fun AppProfile.isVisible(hasGitHubToken: Boolean): Boolean =
    enabled && (latestArtifact?.requiresAuth != true || hasGitHubToken)
