package dev.cl0ud9.krate.platform.backup

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import dev.cl0ud9.krate.domain.model.AutoBackup
import dev.cl0ud9.krate.domain.model.RecommendedSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// how Settings is told which entry to scroll to and highlight
private const val FRAGMENT_ARGS_KEY = ":settings:fragment_args_key"
private const val SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

enum class BackupMode {
    SAVE,
    RESTORE,

    // the catalog's recommended settings laid over the app's own, after keeping a copy of the app's own
    APPLY,

    // a link opened inside the app, and the button it asks for tapped
    LINK,

    // an app's addresses switched on in Android's "Open by default" page, which is Settings' own screen
    OPEN_LINKS,
}

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
        // for recommended settings: how many of them changed something (none: they were already set that way)
        val changed: Int = 0,
        // what the app said on applying, when it said anything ("Imported 12 settings")
        val appSaid: String? = null,
    ) : AutoBackupState

    data class Failed(
        val packageName: String,
        val mode: BackupMode,
        val reason: String,
    ) : AutoBackupState
}

// what the accessibility helper is asked to do
internal const val SETTINGS_PACKAGE = "com.android.settings"

internal data class AutoBackupJob(
    val packageName: String,
    val spec: AutoBackup,
    val mode: BackupMode,
    // for a link: where to go, and the label of the button to tap once the app asks
    val link: String? = null,
    val confirm: String? = null,
    // for OPEN_LINKS: the addresses to switch on, and the apps holding addresses that have to let go of them first
    val hosts: List<String> = emptyList(),
    val releases: List<String> = emptyList(),
    // the text to put back, for a restore; the recommended settings, for applying them
    val text: String? = null,
    // which set of recommended settings, so it's remembered as applied
    val revision: String? = null,
) {
    // the app whose screens the job works in: the app itself, or Settings for its Open by default page
    val watched: String
        get() = if (mode == BackupMode.OPEN_LINKS) SETTINGS_PACKAGE else packageName
}

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
        return start(restoreJob(packageName, spec, text))
    }

    // the catalog's picks over the app's own settings; false when the helper isn't running or a job is already going
    fun applyRecommended(
        packageName: String,
        spec: AutoBackup,
        recommended: RecommendedSettings,
    ): Boolean = start(applyJob(packageName, spec, recommended))

    private val mutableSwitchedOff = MutableStateFlow(0)

    // counts the times Krate switched its helper off itself, so screens showing the switch re-read it at once
    val switchedOff: StateFlow<Int> = mutableSwitchedOff.asStateFlow()

    // the helper switches itself off, the same as turning it off in Android's accessibility settings; false when it
    // isn't running (already off)
    fun turnOff(): Boolean {
        val service = KrateBackupService.running ?: return false
        service.disableSelf()
        mutableSwitchedOff.value++
        return true
    }

    // stops the job going on now, if any; the helper stops tapping at once
    fun cancel() {
        KrateBackupService.running?.interrupt()
    }

    // the result has been shown, so the next look at this app starts clean
    fun acknowledge() {
        if (mutableState.value !is AutoBackupState.Running) mutableState.value = AutoBackupState.Idle
    }

    internal fun start(job: AutoBackupJob): Boolean {
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

// the jobs that go through an app's own screens rather than its backup box: a link opened inside it, and its addresses
// ticked in Android's Open by default page
object AutoLinks {
    // opens [link] inside the app and taps [confirm] when it asks, for a step like "add the store"; false when the
    // helper isn't running or a job is already going
    fun confirmLink(
        packageName: String,
        link: String,
        confirm: String,
    ): Boolean =
        AutoBackupRunner.start(
            AutoBackupJob(packageName, EMPTY_SPEC, BackupMode.LINK, link = link, confirm = confirm),
        )

    // switches [hosts] on for the app in Android's Open by default page; false when the helper isn't running or a job
    // is already going
    fun openLinks(
        packageName: String,
        hosts: List<String>,
        releases: List<String> = emptyList(),
    ): Boolean =
        AutoBackupRunner.start(
            AutoBackupJob(packageName, EMPTY_SPEC, BackupMode.OPEN_LINKS, hosts = hosts, releases = releases),
        )

    private val EMPTY_SPEC = AutoBackup(emptyList(), "", "")
}

// the jobs built by name, never by position: AutoBackupJob has several optional fields before the text, and a text
// handed over by position landed in the wrong one, so restoring and applying pasted nothing
internal fun restoreJob(
    packageName: String,
    spec: AutoBackup,
    text: String,
) = AutoBackupJob(packageName = packageName, spec = spec, mode = BackupMode.RESTORE, text = text)

internal fun applyJob(
    packageName: String,
    spec: AutoBackup,
    recommended: RecommendedSettings,
) = AutoBackupJob(
    packageName = packageName,
    spec = spec,
    mode = BackupMode.APPLY,
    text = recommended.text,
    revision = recommended.revision,
)
