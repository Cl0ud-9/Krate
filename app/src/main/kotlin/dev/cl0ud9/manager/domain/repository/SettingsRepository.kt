package dev.cl0ud9.manager.domain.repository

import kotlinx.coroutines.flow.Flow

// user-controlled preferences, section 42.4 of the spec
interface SettingsRepository {
    fun observeAutomaticDownloads(): Flow<Boolean>

    suspend fun setAutomaticDownloads(enabled: Boolean)

    // whether first-run onboarding (amendment 44.4) has been completed - gates the Apps catalog
    fun observeOnboardingCompleted(): Flow<Boolean>

    suspend fun setOnboardingCompleted()
}
