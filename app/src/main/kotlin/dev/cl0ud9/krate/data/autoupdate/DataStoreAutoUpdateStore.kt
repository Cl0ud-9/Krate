package dev.cl0ud9.krate.data.autoupdate

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.cl0ud9.krate.domain.repository.AutoUpdateChoices
import dev.cl0ud9.krate.domain.repository.AutoUpdateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.autoUpdateDataStore by preferencesDataStore(name = "auto_updates")
private val MANUAL_KEY = stringSetPreferencesKey("manual_apps")
private val SKIPPED_KEY = stringPreferencesKey("skipped_builds")

class DataStoreAutoUpdateStore(
    private val context: Context,
) : AutoUpdateStore {
    private val json = Json { ignoreUnknownKeys = true }

    override fun observeChoices(): Flow<AutoUpdateChoices> =
        context.autoUpdateDataStore.data.map { prefs ->
            AutoUpdateChoices(manual = prefs[MANUAL_KEY].orEmpty(), skipped = prefs.skipped())
        }

    override suspend fun setManual(
        appId: String,
        manual: Boolean,
    ) {
        context.autoUpdateDataStore.edit { prefs ->
            val current = prefs[MANUAL_KEY].orEmpty()
            prefs[MANUAL_KEY] = if (manual) current + appId else current - appId
        }
    }

    override suspend fun skip(
        appId: String,
        buildKey: String?,
    ) {
        context.autoUpdateDataStore.edit { prefs ->
            val updated = if (buildKey == null) prefs.skipped() - appId else prefs.skipped() + (appId to buildKey)
            prefs[SKIPPED_KEY] = json.encodeToString(updated)
        }
    }

    private fun Preferences.skipped(): Map<String, String> =
        this[SKIPPED_KEY]?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() }
            ?: emptyMap()
}
