package dev.cl0ud9.krate.ui.details

import android.content.Context
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.platform.backup.AppBackups
import dev.cl0ud9.krate.platform.backup.AutoBackupState
import dev.cl0ud9.krate.platform.backup.BackupMode
import dev.cl0ud9.krate.platform.backup.RecommendedChoice
import dev.cl0ud9.krate.platform.backup.RecommendedMarks

// the backup kept just before the picks went on is still the latest one, so it can put the app's own settings back,
// whether that was a moment ago or before Krate last closed
internal fun canUndo(
    context: Context,
    app: AppProfile,
    choice: RecommendedChoice,
    state: AutoBackupState?,
): Boolean {
    val nothingChanged = state is AutoBackupState.Done && state.mode == BackupMode.APPLY && state.changed == 0
    val savedAt = AppBackups.read(context, app.packageName)?.savedAtMillis
    val appliedAt = RecommendedMarks.appliedAt(context, app.packageName)
    return choice == RecommendedChoice.APPLIED &&
        !nothingChanged &&
        savedAt != null &&
        appliedAt != null &&
        appliedAt - savedAt in 0..UNDO_WINDOW_MS
}

// how close together the backup and the applying have to be for that backup to be the one from just before
private const val UNDO_WINDOW_MS = 120_000L

internal fun applyText(
    state: AutoBackupState?,
    choice: RecommendedChoice,
): String =
    when {
        state is AutoBackupState.Running -> runningText(state.mode)
        state is AutoBackupState.Failed && state.mode == BackupMode.APPLY -> "${state.reason} Nothing else changed."
        state is AutoBackupState.Done && state.mode == BackupMode.RESTORE -> "Your own settings are back."
        state is AutoBackupState.Done && state.mode == BackupMode.APPLY ->
            appliedText(
                state.changed,
                state.reset,
                state.appSaid,
            )
        choice == RecommendedChoice.OUTDATED -> "Krate's picks have changed since you applied them."
        choice == RecommendedChoice.APPLIED -> "Applied. Apply again if you've changed some of them since."
        else -> "Krate goes to the app's Import / Export screen and makes its settings exactly these."
    }

// what changed, with the app's own word on it when it gave one ("Imported 15 settings"), and how many were put back to
// their default because the picks don't mention them
private fun appliedText(
    changed: Int,
    reset: Int,
    appSaid: String?,
): String {
    if (changed == 0) return "The app already has exactly Krate's settings, so nothing needed changing."
    val confirmed = appSaid?.let { " The app confirmed it: \"${it.trimEnd('.')}\"." }.orEmpty()
    val resetNote = if (reset > 0) " ($reset went back to their default.)" else ""
    return "Done: $changed ${if (changed == 1) "setting" else "settings"} changed.$resetNote$confirmed Some show the " +
        "next time you open the app. Undo puts back the settings you had."
}
