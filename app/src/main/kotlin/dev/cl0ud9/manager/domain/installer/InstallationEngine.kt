package dev.cl0ud9.manager.domain.installer

import dev.cl0ud9.manager.domain.model.AppProfile
import dev.cl0ud9.manager.domain.model.InstallStatus
import kotlinx.coroutines.flow.Flow
import java.io.File

// installs/updates a verified, already-downloaded apk and can uninstall a package, phase 4 + 5 of the
// spec - section 15, 16, 17, 42.12
interface InstallationEngine {
    fun install(
        app: AppProfile,
        apkFile: File,
    ): Flow<InstallStatus>

    fun uninstall(packageName: String): Flow<InstallStatus>
}
