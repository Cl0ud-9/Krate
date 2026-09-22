package dev.cl0ud9.manager.data.baseline

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.cl0ud9.manager.domain.repository.ManagerBaselineStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.baselineDataStore by preferencesDataStore(name = "manager_baselines")
private val BASELINES_KEY = stringPreferencesKey("baselines")

@Serializable
private data class BaselineEntry(
    val packageName: String,
    val versionName: String,
)

// one entry per app, unbounded (unlike the activity log, this can never drop an old entry just for
// being old - a package the manager hasn't touched in months still needs its baseline available)
class DataStoreManagerBaselineStore(
    private val context: Context,
) : ManagerBaselineStore {
    private val json = Json { ignoreUnknownKeys = true }

    override fun observeBaselines(): Flow<Map<String, String>> =
        context.baselineDataStore.data.map { prefs ->
            val raw = prefs[BASELINES_KEY] ?: return@map emptyMap()
            runCatching { json.decodeFromString<List<BaselineEntry>>(raw) }
                .getOrDefault(emptyList())
                .associate { it.packageName to it.versionName }
        }

    override suspend fun recordInstall(
        packageName: String,
        versionName: String,
    ) {
        context.baselineDataStore.edit { prefs ->
            val existing =
                prefs[BASELINES_KEY]?.let { raw ->
                    runCatching { json.decodeFromString<List<BaselineEntry>>(raw) }.getOrDefault(emptyList())
                } ?: emptyList()
            val updated = existing.filterNot { it.packageName == packageName } + BaselineEntry(packageName, versionName)
            prefs[BASELINES_KEY] = json.encodeToString(updated)
        }
    }
}
