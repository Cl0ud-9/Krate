package dev.cl0ud9.krate.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// holds the user's own GitHub personal access token, needed only for artifacts whose
// ArtifactInfo.requiresAuth is true (the invite-only apps, published on a shared *private*
// artifacts repo rather than the public Krate repo). A read-only "Contents" scope on that one
// repo is all it ever needs - unlike a draft release on a public repo, a private repo's published
// release only needs read access to view and download. Kept separate from
// SettingsRepository/DataStore since a real bearer credential belongs in encrypted storage, not
// the plain preferences file the rest of Settings uses
interface GitHubCredentialStore {
    fun getToken(): String?

    // whether a token is saved, shared app-wide so every screen sees a save or removal straight away; a store that
    // can't report changes gives a one-off reading
    val tokenSaved: StateFlow<Boolean>
        get() = MutableStateFlow(getToken() != null)

    fun setToken(token: String)

    fun clearToken()
}
