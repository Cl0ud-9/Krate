package dev.cl0ud9.krate.platform.rollback

import java.io.File

// preserves one previous apk per app locally so a failed clean install can be restored,
// section 21 + 42.13 of the spec - a recovery mechanism, not a cloud backup
interface RollbackStore {
    // copies the currently-installed apk for packageName into local storage, true if it now exists
    fun capture(packageName: String): Boolean

    fun rollbackFile(packageName: String): File?

    // the copy is only kept while it can still matter; once the app is safely installed again it goes
    fun discard(packageName: String)

    // every kept copy, for Settings' Clear download cache; returns the bytes freed
    fun clearAll(): Long
}
