package dev.cl0ud9.manager.data.auth

// holds the user's own GitHub personal access token, needed only for artifacts whose
// ArtifactInfo.requiresAuth is true (the Patched-style apps, published on a shared *private*
// artifacts repo rather than the public manager repo). A read-only "Contents" scope on that one
// repo is all it ever needs - unlike a draft release on a public repo, a private repo's published
// release only needs read access to view and download, see SETUP.md section 4. Kept separate from
// SettingsRepository/DataStore since a real bearer credential belongs in encrypted storage, not
// the plain preferences file the rest of Settings uses
interface GitHubCredentialStore {
    fun getToken(): String?

    fun setToken(token: String)

    fun clearToken()
}
