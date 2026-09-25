package dev.cl0ud9.manager.data.announcements

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.cl0ud9.manager.domain.repository.AnnouncementDismissalStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.announcementDataStore by preferencesDataStore(name = "announcements")
private val DISMISSED_KEY = stringSetPreferencesKey("dismissed")

class DataStoreAnnouncementDismissalStore(
    private val context: Context,
) : AnnouncementDismissalStore {
    override fun observeDismissed(): Flow<Set<String>> =
        context.announcementDataStore.data.map { prefs -> prefs[DISMISSED_KEY].orEmpty() }

    override suspend fun dismiss(id: String) {
        context.announcementDataStore.edit { prefs -> prefs[DISMISSED_KEY] = prefs[DISMISSED_KEY].orEmpty() + id }
    }
}
