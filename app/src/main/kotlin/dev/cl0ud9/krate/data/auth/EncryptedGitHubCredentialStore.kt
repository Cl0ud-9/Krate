package dev.cl0ud9.krate.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.KeyStore

private const val PREFS_FILE_NAME = "github_credentials"
private const val KEY_TOKEN = "github_pat"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val TAG = "KrateCredentials"

// backed by Android Keystore via Jetpack Security, not the app's plain DataStore file, since this
// holds a real bearer credential rather than a UI preference
class EncryptedGitHubCredentialStore(
    private val context: Context,
) : GitHubCredentialStore {
    // null only when this phone's secure storage won't open at all
    private val prefs: SharedPreferences? by lazy { openPrefs() }

    // a token that couldn't be stored safely lasts until Krate closes, and is never written anywhere in the clear
    @Volatile
    private var unsavedToken: String? = null

    private val mutableTokenSaved by lazy { MutableStateFlow(getToken() != null) }
    override val tokenSaved: StateFlow<Boolean> get() = mutableTokenSaved

    // .trim() here too, not just in setToken - an already-saved token from before that trim existed
    // would otherwise keep failing every download until the user notices and manually re-enters it
    override fun getToken(): String? =
        (runCatching { prefs?.getString(KEY_TOKEN, null) }.getOrNull() ?: unsavedToken)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    // trimmed here, not just at the UI layer - a token copied from a terminal or a file often
    // carries a trailing newline, and OkHttp's header validation rejects any control character in
    // an Authorization value outright ("Unexpected char 0x0a..."), failing every download until the
    // user notices and manually re-types it
    override fun setToken(token: String) {
        val clean = token.trim()
        val saved = runCatching { prefs?.edit { putString(KEY_TOKEN, clean) } != null }.getOrDefault(false)
        unsavedToken = clean.takeUnless { saved }
        mutableTokenSaved.value = getToken() != null
    }

    override fun clearToken() {
        runCatching { prefs?.edit { remove(KEY_TOKEN) } }
        unsavedToken = null
        mutableTokenSaved.value = false
    }

    // a token file that reached this phone without its key can never be read, so it goes and the token is added again
    private fun openPrefs(): SharedPreferences? =
        runCatching { buildEncryptedPrefs() }
            .recoverCatching { unreadable ->
                Log.w(TAG, "saved token can't be read, starting again", unreadable)
                context.deleteSharedPreferences(PREFS_FILE_NAME)
                KeyStore.getInstance(ANDROID_KEYSTORE).apply {
                    load(null)
                    deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                }
                buildEncryptedPrefs()
            }.onFailure { Log.w(TAG, "secure storage won't open", it) }
            .getOrNull()

    private fun buildEncryptedPrefs(): SharedPreferences {
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
