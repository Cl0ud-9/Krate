package dev.cl0ud9.krate.data.tracked

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.cl0ud9.krate.domain.model.AppProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val Context.trackedDataStore by preferencesDataStore(name = "krate_tracked_apps")
private val ENTRIES_KEY = stringPreferencesKey("entries")

// how a check of tracked apps went, for anything that wants to say so
sealed interface TrackedCheck {
    data object Done : TrackedCheck

    // a site's allowance ran out partway; the rest keep what they had
    data class OutOfChecks(
        val withToken: Boolean,
    ) : TrackedCheck

    data object Offline : TrackedCheck
}

// the apps someone tracks themselves, kept on the phone, with each one's latest releases
class TrackedAppsRepository(
    private val context: Context,
    private val releases: ForgeReleases,
    private val supportedAbis: List<String>,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeEntries(): Flow<List<TrackedEntry>> =
        context.trackedDataStore.data.map {
            decodeEntries(json, it[ENTRIES_KEY])
        }

    fun observeApps(): Flow<List<AppProfile>> = observeEntries().map { entries -> entries.map { it.toProfile() } }

    suspend fun entries(): List<TrackedEntry> = observeEntries().first()

    suspend fun add(entry: TrackedEntry) = edit { entries -> entries.filterNot { it.id == entry.id } + entry }

    // stops tracking it on Krate's own time, so leaving the page straight after can't cut it off halfway
    fun forget(id: String) {
        backgroundScope.launch { edit { entries -> entries.filterNot { it.id == id } } }
    }

    // each app's newest releases. One at a time, and each saved on its own, so an app added or removed meanwhile
    // stays added or removed
    suspend fun refresh(): TrackedCheck =
        withContext(Dispatchers.IO) {
            var result: TrackedCheck = TrackedCheck.Done
            for (entry in entries()) {
                when (val check = check(entry)) {
                    is TrackedCheck.OutOfChecks -> return@withContext check
                    TrackedCheck.Offline -> result = check
                    TrackedCheck.Done -> Unit
                }
            }
            result
        }

    // test releases on or off for one app; its list is read again from scratch, as the saved one was cut to suit
    fun setPrerelease(
        id: String,
        include: Boolean,
    ) {
        backgroundScope.launch {
            edit { all -> all.map { if (it.id == id) it.copy(includePrerelease = include, etag = null) else it } }
            entries().find { it.id == id }?.let { check(it) }
        }
    }

    private suspend fun check(entry: TrackedEntry): TrackedCheck =
        when (val answer = releases.releases(entry.ref, entry.etag)) {
            is ForgeAnswer.Found -> {
                val builds = buildsFrom(answer.value, entry.fileShape, entry.includePrerelease, supportedAbis)
                // a list with nothing usable keeps the last good one rather than emptying the app
                if (builds.isNotEmpty()) edit { all -> all.withBuilds(entry.id, builds, answer.etag) }
                TrackedCheck.Done
            }
            is ForgeAnswer.OutOfChecks -> TrackedCheck.OutOfChecks(answer.withToken)
            ForgeAnswer.Unreachable -> TrackedCheck.Offline
            ForgeAnswer.Unchanged, ForgeAnswer.NotFound -> TrackedCheck.Done
        }

    private suspend fun edit(transform: (List<TrackedEntry>) -> List<TrackedEntry>) {
        context.trackedDataStore.edit { prefs -> prefs.update(transform) }
    }

    private fun MutablePreferences.update(transform: (List<TrackedEntry>) -> List<TrackedEntry>) {
        this[ENTRIES_KEY] = json.encodeToString(transform(decodeEntries(json, this[ENTRIES_KEY])))
    }
}

private fun decodeEntries(
    json: Json,
    raw: String?,
): List<TrackedEntry> =
    raw?.let { runCatching { json.decodeFromString<List<TrackedEntry>>(it) }.getOrNull() } ?: emptyList()

private fun List<TrackedEntry>.withBuilds(
    id: String,
    builds: List<TrackedBuild>,
    etag: String?,
): List<TrackedEntry> = map { if (it.id == id) it.copy(builds = builds, etag = etag) else it }
