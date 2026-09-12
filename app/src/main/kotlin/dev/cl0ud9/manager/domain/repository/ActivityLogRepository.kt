package dev.cl0ud9.manager.domain.repository

import dev.cl0ud9.manager.domain.model.ActivityEntry
import kotlinx.coroutines.flow.Flow

// backs the Home screen's Recent activity section with genuine local history instead of a
// permanently-empty placeholder - every entry is recorded from a real completed install/update
interface ActivityLogRepository {
    fun observeRecent(): Flow<List<ActivityEntry>>

    suspend fun record(entry: ActivityEntry)
}
