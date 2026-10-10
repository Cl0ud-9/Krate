package dev.cl0ud9.krate.domain.model

// one downloadable build of an app, section 9 of the spec - AppProfile.artifacts holds every
// build currently retained that fits this device (newest first), not just the latest, so a broken
// newest build still leaves older ones installable
// requiresAuth is true only for artifacts hosted as a private GitHub release asset (the
// invite-only apps, kept off public releases) - downloadUrl is then a
// api.github.com/repos/.../releases/assets/{id} URL rather than a plain browser_download_url, and
// needs a bearer token the download engine reads from GitHubCredentialStore
// patchesVersionName is the version of the *tool* that built this artifact (e.g. a patches
// bundle), distinct from versionName (the app's own version) - null for
// artifacts with no such intermediate build tool
// buildId tells apart two builds of the same versionName (a rebuild with newer patches
// keeps the app's own version), and is null only in manifests older than schema 2
data class ArtifactInfo(
    val versionName: String,
    val downloadUrl: String,
    val sha256: String,
    val certificateSha256: String,
    val requiresAuth: Boolean = false,
    val patchesVersionName: String? = null,
    val versionCode: Long? = null,
    val buildId: String? = null,
    val label: String? = null,
    val note: String? = null,
    val releaseNotes: String? = null,
    val publishedAtMillis: Long? = null,
    val withdrawn: Boolean = false,
    val withdrawnReason: String? = null,
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
    // newest first; empty for a local seed entry with nothing real to download yet
    val artifacts: List<ArtifactInfo>,
    // launcher icon from the catalog (base64 PNG), shown until the app is installed on the device
    val iconPng: String? = null,
    // what the app is for, in a line, and a few things it does - absent from older catalogs
    val description: String? = null,
    val highlights: List<String> = emptyList(),
    // setup steps, settings tips and backup steps; absent from older catalogs and for apps that need none
    val guide: AppGuide? = null,
    // owner/repo for an app someone tracks themselves; null for the catalog's own apps
    val trackedRepo: String? = null,
    // the site a tracked app comes from, as Forge's id (github, codeberg, gitlab)
    val trackedForge: String? = null,
    // a tracked app's test releases are offered too
    val trackedPrerelease: Boolean = false,
)

// added by the person themselves, rather than picked for the catalog
val AppProfile.isTracked: Boolean
    get() = trackedRepo != null

// a withdrawn build stays listed in version history but is never the one offered as the update
val AppProfile.latestArtifact: ArtifactInfo?
    get() = artifacts.firstOrNull { !it.withdrawn }

// kept as a computed property (not a stored field) so there is exactly one source of truth for
// "the app's newest version" - every call site reads this instead, and it can never drift out of
// sync with artifacts
val AppProfile.latestVersionName: String?
    get() = latestArtifact?.versionName

// an app only belongs in front-of-user surfaces (Apps/Home/Updates) when the catalog's own
// `enabled` kill switch is on, and - for an artifact hosted as a private release asset, currently
// the invite-only apps - only once a GitHub token is actually present. Without a token there is
// nothing it could do (its download would just fail with a 401), so it should not be offered as an
// option in the first place rather than shown and then broken. Dependency resolution deliberately
// does not use this: a dependency must still resolve against the full catalog regardless of whether
// it would itself be visible if browsed directly
fun AppProfile.isVisible(hasGitHubToken: Boolean): Boolean =
    enabled && (latestArtifact?.requiresAuth != true || hasGitHubToken)
