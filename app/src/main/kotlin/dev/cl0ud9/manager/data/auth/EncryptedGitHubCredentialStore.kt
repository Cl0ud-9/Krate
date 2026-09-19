package dev.cl0ud9.manager.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val PREFS_FILE_NAME = "github_credentials"
private const val KEY_TOKEN = "github_pat"

// backed by Android Keystore via Jetpack Security, not the app's plain DataStore file, since this
// holds a real bearer credential rather than a UI preference
class EncryptedGitHubCredentialStore(
    context: Context,
) : GitHubCredentialStore {
    private val prefs: SharedPreferences by lazy { buildEncryptedPrefs(context) }

    // .trim() here too, not just in setToken - an already-saved token from before that trim existed
    // would otherwise keep failing every download until the user notices and manually re-enters it
    override fun getToken(): String? = prefs.getString(KEY_TOKEN, null)?.trim()?.takeIf { it.isNotBlank() }

    // trimmed here, not just at the UI layer - a token copied from a terminal or a file often
    // carries a trailing newline, and OkHttp's header validation rejects any control character in
    // an Authorization value outright ("Unexpected char 0x0a..."), failing every download until the
    // user notices and manually re-types it
    override fun setToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    override fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    private fun buildEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
