package dev.cl0ud9.krate.platform.backup

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import dev.cl0ud9.krate.KrateActivity
import dev.cl0ud9.krate.domain.model.UiTarget
import dev.cl0ud9.krate.domain.settings.SettingsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LAUNCH_SETTLE_MS = 2_500L
private const val STEP_SETTLE_MS = 900L
private const val POLL_MS = 300L
private const val FIND_TIMEOUT_MS = 6_000L
private const val SCROLL_AFTER_MS = 1_500L
private const val MAX_SCROLLS = 6
private const val MAX_BACKS = 8

// looks for an active window before deciding there isn't one
private const val ACTIVE_LOOKS = 5
private const val TAP_MS = 60L
private const val DIALOG_CHECK_MS = 3_000L
private const val FULL_LOOK_MS = FIND_TIMEOUT_MS + MAX_SCROLLS * SCROLL_AFTER_MS

// notices an app can stack up as it opens, each closed with Back
private const val MAX_DIALOGS = 3

// how long to wait for the app to say how an import went, in the short message it shows; some take a few seconds
private const val APP_MESSAGE_MS = 8_000L

// where Android draws an app's short messages
private const val SYSTEM_UI = "com.android.systemui"

// words in that message that mean the app turned the settings down
private val REFUSALS = listOf("fail", "error", "invalid", "couldn't", "could not", "unable")

// what a walk through the app's backup box came back with
private class WalkResult(
    // the text read out, for a backup
    val text: String? = null,
    // how many recommended settings changed something
    val changed: Int = 0,
    // what the app said on importing, if anything
    val appSaid: String? = null,
)

// a step that couldn't be found, named for the message shown afterwards
private class StepNotFound(
    val step: String,
) : Exception()

// Krate's accessibility helper: when asked, it opens an app, taps its way to that app's own backup box, and reads
// the settings out of it or pastes them back in. It does nothing in between, and only looks at the app it's for
// one walk through an app's backup box, its steps kept together so the whole of what it does reads in one place
@Suppress("TooManyFunctions")
class KrateBackupService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onServiceConnected() {
        running = this
        listenTo(packageName)
    }

    // the app's short messages ("Imported 12 settings"), heard only while a job runs in that app
    private var jobPackage: String? = null
    private var appMessage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return
        // a notification carries its own data; a short on-screen message carries none. Android draws an app's short
        // messages in its system interface, which may be reported as their sender
        val sender = event.packageName?.toString()
        val fromApp = sender == jobPackage || (sender == SYSTEM_UI && event.className == Toast::class.java.name)
        if (jobPackage == null || !fromApp || event.parcelableData != null) return
        // the message first; Android adds the app's name after it
        event.text
            .firstOrNull()
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { appMessage = it }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (running === this) running = null
        scope.cancel()
        super.onDestroy()
    }

    internal fun run(job: AutoBackupJob) {
        scope.launch {
            val result =
                try {
                    listenTo(job.packageName)
                    jobPackage = job.packageName
                    val walked = walk(job)
                    val text = walked.text
                    if (!text.isNullOrBlank()) AppBackups.save(this@KrateBackupService, job.packageName, text)
                    if (job.mode == BackupMode.APPLY) {
                        job.revision?.let { RecommendedMarks.applied(this@KrateBackupService, job.packageName, it) }
                    }
                    AutoBackupState.Done(
                        job.packageName,
                        job.mode,
                        nothingToSave = job.mode == BackupMode.SAVE && text.isNullOrBlank(),
                        changed = walked.changed,
                        appSaid = walked.appSaid,
                    )
                } catch (missing: StepNotFound) {
                    AutoBackupState.Failed(job.packageName, job.mode, "Couldn't find \"${missing.step}\" in the app.")
                } catch (failure: IllegalStateException) {
                    AutoBackupState.Failed(job.packageName, job.mode, failure.message ?: "Something got in the way.")
                }
            jobPackage = null
            leave(job.packageName)
            listenTo(packageName)
            AutoBackupRunner.finish(result)
            returnToKrate()
        }
    }

    // from the app's first screen to its backup box; the text read out, for a backup
    private suspend fun walk(job: AutoBackupJob): WalkResult {
        val launch =
            packageManager.getLaunchIntentForPackage(job.packageName)
                ?: error("The app isn't installed.")
        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        delay(LAUNCH_SETTLE_MS)
        job.spec.path.forEachIndexed { index, target ->
            tap(job.packageName, target, dismissFirst = index == 0)
            delay(STEP_SETTLE_MS)
        }
        val field = waitFor(job.packageName) { root -> root.find { it.isEditable } } ?: throw StepNotFound("text box")
        // an empty box can report its hint as its text; an app with nothing changed from its defaults exports nothing,
        // which isn't a failure
        val boxText = if (field.isShowingHintText) "" else field.text?.toString().orEmpty()
        return when (job.mode) {
            BackupMode.SAVE -> {
                tap(job.packageName, UiTarget(text = job.spec.close))
                WalkResult(text = boxText)
            }
            BackupMode.RESTORE -> WalkResult(appSaid = paste(job, field, job.text.orEmpty()))
            BackupMode.APPLY -> applyRecommended(job, field, boxText)
        }
    }

    // the app's own settings with the recommended ones laid over them; a copy of the app's own is kept first, so
    // they can be put back. Nothing is pasted when the picks are all set already, or the box can't be read
    private suspend fun applyRecommended(
        job: AutoBackupJob,
        field: AccessibilityNodeInfo,
        own: String,
    ): WalkResult {
        val picks = job.text.orEmpty()
        val ownSettings = SettingsText.parse(own)
        val pickedSettings = SettingsText.parse(picks)
        if (ownSettings == null || pickedSettings == null) {
            tap(job.packageName, UiTarget(text = job.spec.close))
            error("Couldn't read the app's current settings, so nothing was changed.")
        }
        val changed = SettingsText.changes(ownSettings, pickedSettings)
        if (changed.isEmpty()) {
            tap(job.packageName, UiTarget(text = job.spec.close))
            return WalkResult()
        }
        if (own.isNotBlank()) AppBackups.save(this, job.packageName, own)
        val braces = SettingsText.usesBraces(own, picks)
        val merged = SettingsText.write(SettingsText.merge(ownSettings, pickedSettings), braces)
        return WalkResult(changed = changed.size, appSaid = paste(job, field, merged))
    }

    // puts the text in the box and applies it; what the app said back, failing if it turned the text down
    private suspend fun paste(
        job: AutoBackupJob,
        field: AccessibilityNodeInfo,
        text: String,
    ): String? {
        val arguments =
            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        if (!field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
            error("Couldn't paste into the backup box.")
        }
        delay(STEP_SETTLE_MS)
        appMessage = null
        tap(job.packageName, UiTarget(text = job.spec.apply))
        var waited = 0L
        while (appMessage == null && waited < APP_MESSAGE_MS) {
            delay(POLL_MS)
            waited += POLL_MS
        }
        val said = appMessage
        if (said != null && REFUSALS.any { said.contains(it, ignoreCase = true) }) error("The app said: $said")
        return said
    }

    // a dialog the app shows on opening can cover the first step, so that one gets a Back first if it won't show
    private suspend fun tap(
        packageName: String,
        target: UiTarget,
        dismissFirst: Boolean = false,
    ) {
        // a dialog over the first screen hides what is under it: a short look and Back, a few times, then a full look
        var node: AccessibilityNodeInfo? = null
        var dismissals = if (dismissFirst) MAX_DIALOGS else 0
        while (node == null && dismissals > 0) {
            node = waitFor(packageName, DIALOG_CHECK_MS) { root -> root.match(target) }
            if (node == null) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(STEP_SETTLE_MS)
            }
            dismissals--
        }
        // a step only some versions of the app's screens have gets the short look, and is skipped when it isn't there
        if (node ==
            null
        ) {
            node =
                waitFor(
                    packageName,
                    if (target.optional) DIALOG_CHECK_MS else FULL_LOOK_MS,
                ) { root -> root.match(target) }
        }
        if (node == null && target.optional) return
        val found = node ?: throw StepNotFound(target.text ?: target.description.orEmpty())
        val clickable = found.clickableSelfOrAncestor()
        if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) != true) tapAt(found)
    }

    // backs out of the app's screens it opened, all the way, so the app opens on its own first screen next time rather
    // than on the settings page Krate left it at. A box that's closing can leave no window active for a moment, so
    // that gets a short wait rather than being taken for the app having gone
    private suspend fun leave(packageName: String) {
        delay(STEP_SETTLE_MS)
        repeat(MAX_BACKS) {
            if (activePackage() != packageName) return
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(POLL_MS * 2)
        }
    }

    // the app in the active window, given a moment to settle when none is reported
    private suspend fun activePackage(): String? {
        repeat(ACTIVE_LOOKS) {
            rootInActiveWindow?.packageName?.toString()?.let { return it }
            delay(POLL_MS)
        }
        return null
    }

    private fun returnToKrate() {
        val intent =
            Intent(this, KrateActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runCatching { startActivity(intent) }
    }

    // events and windows from this one app only: Krate's own while idle, the app being backed up while a job runs
    private fun listenTo(packageName: String) {
        val info = serviceInfo ?: return
        // while a job runs, the system interface too, only for the app's short messages it draws
        val messages = if (packageName == this.packageName) emptyArray() else arrayOf(SYSTEM_UI)
        info.packageNames = arrayOf(packageName, this.packageName) + messages
        serviceInfo = info
    }

    companion object {
        @Volatile
        internal var running: KrateBackupService? = null
    }
}

// looks in the app's windows until the node turns up, scrolling down a little at a time when it's not in view
private suspend fun AccessibilityService.waitFor(
    packageName: String,
    timeout: Long = FULL_LOOK_MS,
    find: (AccessibilityNodeInfo) -> AccessibilityNodeInfo?,
): AccessibilityNodeInfo? {
    var waited = 0L
    var scrolls = 0
    while (waited < timeout) {
        appWindows(packageName).firstNotNullOfOrNull(find)?.let { return it }
        if (timeout > DIALOG_CHECK_MS && waited >= SCROLL_AFTER_MS * (scrolls + 1) && scrolls < MAX_SCROLLS) {
            val scrollable = appWindows(packageName).firstNotNullOfOrNull { root -> root.find { it.isScrollable } }
            if (scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ==
                true
            ) {
                scrolls++
            } else {
                scrolls = MAX_SCROLLS
            }
        }
        delay(POLL_MS)
        waited += POLL_MS
    }
    return null
}

// the app's windows, the topmost (a dialog) first
private fun AccessibilityService.appWindows(packageName: String): List<AccessibilityNodeInfo> =
    windows
        .sortedByDescending { it.layer }
        .mapNotNull { it.root }
        .filter { it.packageName?.toString() == packageName }

private fun AccessibilityService.tapAt(node: AccessibilityNodeInfo) {
    val bounds = Rect().also(node::getBoundsInScreen)
    val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
    dispatchGesture(
        GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, TAP_MS)).build(),
        null,
        null,
    )
}

// the last match on screen that can be tapped, or the last match at all: a screen's title can carry the same words
// as the row that opens the next screen, and the title comes first
private fun AccessibilityNodeInfo.match(target: UiTarget): AccessibilityNodeInfo? {
    val matches =
        findAll { node ->
            val text = node.text?.toString()?.trim()
            val description = node.contentDescription?.toString()?.trim()
            (target.text != null && text.equals(target.text, ignoreCase = true)) ||
                (target.description != null && description.equals(target.description, ignoreCase = true))
        }
    return matches.lastOrNull { it.clickableSelfOrAncestor() != null } ?: matches.lastOrNull()
}

private fun AccessibilityNodeInfo.find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? =
    findAll(predicate).firstOrNull()

private fun AccessibilityNodeInfo.findAll(predicate: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
    val found = mutableListOf<AccessibilityNodeInfo>()
    val queue = ArrayDeque(listOf(this))
    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        if (node.isVisibleToUser && predicate(node)) found += node
        for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
    }
    return found
}

private fun AccessibilityNodeInfo.clickableSelfOrAncestor(): AccessibilityNodeInfo? {
    var node: AccessibilityNodeInfo? = this
    while (node != null && !node.isClickable) node = node.parent
    return node
}
