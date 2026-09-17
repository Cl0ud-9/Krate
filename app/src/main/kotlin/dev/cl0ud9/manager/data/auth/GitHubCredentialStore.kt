package dev.cl0ud9.manager.data.auth

// holds the user's own GitHub personal access token, needed only for artifacts whose
// ArtifactInfo.requiresAuth is true (currently just Videoapp Patched, published as a private/draft
// release asset rather than a public one). Read-only "Contents"/"Releases" scope on this one repo
// is all it needs. Kept separate from SettingsRepository/DataStore since a real bearer credential
// belongs in encrypted storage, not the plain preferences file the rest of Settings uses
interface GitHubCredentialStore {
    fun getToken(): String?

    fun setToken(token: String)

    fun clearToken()
}
