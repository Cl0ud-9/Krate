package dev.cl0ud9.krate.data.baseline

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.cl0ud9.krate.domain.model.ArtifactInfo
import dev.cl0ud9.krate.domain.repository.Baseline
import dev.cl0ud9.krate.domain.repository.KrateBaselineStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.baselineDataStore by preferencesDataStore(name = "krate_baselines")
private val BASELINES_KEY = stringPreferencesKey("baselines")

// buildId defaults to null so entries written by 0.1.5 (no build ids yet) still decode
@Serializable
private data class BaselineEntry(
    val packageName: String,
    val versionName: String,
    val buildId: String? = null,
)

// one entry per app, unbounded (unlike the activity log, this can never drop an old entry just for
// being old - a package Krate hasn't touched in months still needs its baseline available)
class DataStoreKrateBaselineStore(
    private val context: Context,
) : KrateBaselineStore {
    private val json = Json { ignoreUnknownKeys = true }

    override fun observeBaselines(): Flow<Map<String, Baseline>> =
        context.baselineDataStore.data.map { prefs ->
            decode(prefs[BASELINES_KEY]).associate { it.packageName to Baseline(it.versionName, it.buildId) }
        }

    override suspend fun recordInstall(
        packageName: String,
        artifact: ArtifactInfo,
    ) {
        context.baselineDataStore.edit { prefs ->
            prefs.update { entries ->
                entries.filterNot { it.packageName == packageName } +
                    BaselineEntry(packageName, artifact.versionName, artifact.buildId)
            }
        }
    }

    override suspend fun clear(packageName: String) {
        context.baselineDataStore.edit { prefs ->
            prefs.update { entries -> entries.filterNot { it.packageName == packageName } }
        }
    }

    private fun MutablePreferences.update(transform: (List<BaselineEntry>) -> List<BaselineEntry>) {
        this[BASELINES_KEY] = json.encodeToString(transform(decode(this[BASELINES_KEY])))
    }

    private fun decode(raw: String?): List<BaselineEntry> =
        raw?.let { runCatching { json.decodeFromString<List<BaselineEntry>>(it) }.getOrNull() } ?: emptyList()
}
