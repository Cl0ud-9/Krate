package dev.cl0ud9.manager.data.activity

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.cl0ud9.manager.domain.model.ActivityAction
import dev.cl0ud9.manager.domain.model.ActivityEntry
import dev.cl0ud9.manager.domain.repository.ActivityLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.activityDataStore by preferencesDataStore(name = "activity_log")
private val ENTRIES_KEY = stringPreferencesKey("entries")
private const val MAX_ENTRIES = 20

@Serializable
private data class ActivityEntryDto(
    val id: String,
    val appId: String,
    val appName: String,
    val action: String,
    val timestampMillis: Long,
)

private fun ActivityEntryDto.toDomain(): ActivityEntry =
    ActivityEntry(
        id = id,
        appId = appId,
        appName = appName,
        action = runCatching { ActivityAction.valueOf(action) }.getOrDefault(ActivityAction.UPDATED),
        timestampMillis = timestampMillis,
    )

private fun ActivityEntry.toDto(): ActivityEntryDto = ActivityEntryDto(id, appId, appName, action.name, timestampMillis)

// a small local history of completed installs/updates/uninstalls, capped to MAX_ENTRIES newest-first
// - backs the Home screen's Recent activity section with real data instead of a permanent placeholder
class DataStoreActivityLogRepository(
    private val context: Context,
) : ActivityLogRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override fun observeRecent(): Flow<List<ActivityEntry>> =
        context.activityDataStore.data.map { prefs ->
            val raw = prefs[ENTRIES_KEY] ?: return@map emptyList()
            runCatching { json.decodeFromString<List<ActivityEntryDto>>(raw) }
                .getOrDefault(emptyList())
                .map { it.toDomain() }
        }

    override suspend fun record(entry: ActivityEntry) {
        context.activityDataStore.edit { prefs ->
            val existing =
                prefs[ENTRIES_KEY]?.let { raw ->
                    runCatching { json.decodeFromString<List<ActivityEntryDto>>(raw) }.getOrDefault(emptyList())
                } ?: emptyList()
            val updated = (listOf(entry.toDto()) + existing).take(MAX_ENTRIES)
            prefs[ENTRIES_KEY] = json.encodeToString(updated)
        }
    }
}
