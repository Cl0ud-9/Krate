package dev.cl0ud9.manager.domain.installer

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.InstallStatus
import kotlinx.coroutines.flow.Flow
import java.io.File

// installs/updates a verified, already-downloaded apk, phase 4 of the spec - section 15, 16, 42.12
interface InstallationEngine {
    fun install(
        app: AppProfile,
        apkFile: File,
    ): Flow<InstallStatus>
}
