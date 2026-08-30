package dev.cl0ud9.manager.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dev.cl0ud9.manager.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(
    private val context: Context,
) : SettingsRepository {
    override fun observeAutomaticDownloads(): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[AUTOMATIC_DOWNLOADS] ?: true }

    override suspend fun setAutomaticDownloads(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[AUTOMATIC_DOWNLOADS] = enabled }
    }

    private companion object {
        // default ON per section 42.4 of the spec
        val AUTOMATIC_DOWNLOADS = booleanPreferencesKey("automatic_downloads")
    }
}
