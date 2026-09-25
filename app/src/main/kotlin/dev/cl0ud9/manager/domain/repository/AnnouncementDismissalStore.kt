package dev.cl0ud9.manager.domain.repository

import kotlinx.coroutines.flow.Flow

// ids of catalog announcements the user closed - an id is never reused for a different notice, so
// a dismissal is simply remembered forever
interface AnnouncementDismissalStore {
    fun observeDismissed(): Flow<Set<String>>

    suspend fun dismiss(id: String)
}
