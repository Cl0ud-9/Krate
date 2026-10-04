package dev.cl0ud9.krate.platform.backup

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import dev.cl0ud9.krate.domain.model.AutoBackup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// how Settings is told which entry to scroll to and highlight
private const val FRAGMENT_ARGS_KEY = ":settings:fragment_args_key"
private const val SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

enum class BackupMode { SAVE, RESTORE }

// where a backup or restore Krate runs by itself stands; one at a time, for one app
sealed interface AutoBackupState {
    data object Idle : AutoBackupState

    data class Running(
        val packageName: String,
        val mode: BackupMode,
    ) : AutoBackupState

    data class Done(
        val packageName: String,
        val mode: BackupMode,
        // the app's settings were all at their defaults, so there was nothing to keep
        val nothingToSave: Boolean = false,
    ) : AutoBackupState

    data class Failed(
        val packageName: String,
        val mode: BackupMode,
        val reason: String,
    ) : AutoBackupState
}

// what the accessibility helper is asked to do
internal data class AutoBackupJob(
    val packageName: String,
    val spec: AutoBackup,
    val mode: BackupMode,
    // the text to put back, for a restore
    val text: String? = null,
)

// starts a backup or restore through Krate's accessibility helper and reports how it went. The helper only acts
// while a job runs, and only inside the app it's for
object AutoBackupRunner {
    private val mutableState = MutableStateFlow<AutoBackupState>(AutoBackupState.Idle)
    val state: StateFlow<AutoBackupState> = mutableState.asStateFlow()

    // whether the helper is switched on in Android's accessibility settings and running
    fun isOn(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val ours = ComponentName(context, KrateBackupService::class.java)
        return enabled.orEmpty().split(':').any { ComponentName.unflattenFromString(it) == ours }
    }

    // Android's accessibility list (its page for one service needs a system permission), with Krate's entry marked
    // where the phone supports that
    fun settingsIntent(context: Context): Intent {
        val component = ComponentName(context, KrateBackupService::class.java).flattenToString()
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(FRAGMENT_ARGS_KEY, component)
            .putExtra(SHOW_FRAGMENT_ARGS, Bundle().apply { putString(FRAGMENT_ARGS_KEY, component) })
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // false when the helper isn't running or a job is already going
    fun save(
        packageName: String,
        spec: AutoBackup,
    ): Boolean = start(AutoBackupJob(packageName, spec, BackupMode.SAVE))

    fun restore(
        context: Context,
        packageName: String,
        spec: AutoBackup,
    ): Boolean {
        val text = AppBackups.read(context, packageName)?.text ?: return false
        return start(AutoBackupJob(packageName, spec, BackupMode.RESTORE, text))
    }

    // the result has been shown, so the next look at this app starts clean
    fun acknowledge() {
        if (mutableState.value !is AutoBackupState.Running) mutableState.value = AutoBackupState.Idle
    }

    private fun start(job: AutoBackupJob): Boolean {
        val service = KrateBackupService.running?.takeIf { mutableState.value !is AutoBackupState.Running }
        service?.let {
            mutableState.value = AutoBackupState.Running(job.packageName, job.mode)
            it.run(job)
        }
        return service != null
    }

    internal fun finish(result: AutoBackupState) {
        mutableState.value = result
    }
}
