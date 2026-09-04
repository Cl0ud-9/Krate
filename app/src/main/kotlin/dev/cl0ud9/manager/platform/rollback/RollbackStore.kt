package dev.cl0ud9.manager.platform.rollback

import java.io.File

// preserves one previous apk per app locally so a failed clean install can be restored,
// section 21 + 42.13 of the spec - a recovery mechanism, not a cloud backup
interface RollbackStore {
    // copies the currently-installed apk for packageName into local storage, true if it now exists
    fun capture(packageName: String): Boolean

    fun rollbackFile(packageName: String): File?
}
