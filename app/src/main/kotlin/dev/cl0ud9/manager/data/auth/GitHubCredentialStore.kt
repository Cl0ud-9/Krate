package dev.cl0ud9.manager.data.auth

// holds the user's own GitHub personal access token, needed only for artifacts whose
// ArtifactInfo.requiresAuth is true (currently just Videoapp Patched, published as a private/draft
// release asset rather than a public one). Needs "Contents: Read and write" scope on this one repo,
// not read-only: per GitHub's own REST API docs, a draft release's listing and assets are only
// visible to users with push access, so a read-only token gets a 403/404 trying to fetch them - see
// SETUP.md section 4. Kept separate from SettingsRepository/DataStore since a real bearer credential
// belongs in encrypted storage, not the plain preferences file the rest of Settings uses
interface GitHubCredentialStore {
    fun getToken(): String?

    fun setToken(token: String)

    fun clearToken()
}
