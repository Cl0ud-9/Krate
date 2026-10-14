package dev.cl0ud9.krate.platform.backup

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import dev.cl0ud9.krate.KrateActivity
import dev.cl0ud9.krate.domain.model.UiTarget
import dev.cl0ud9.krate.domain.settings.SettingsText
import dev.cl0ud9.krate.platform.diagnostics.KrateLog
import dev.cl0ud9.krate.platform.links.OpenByDefault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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

// the longest a whole job may take before it's given up on, whatever it's stuck on
private const val JOB_TIMEOUT_MS = 120_000L

// system screens that can appear over the app being worked in (a permission question, say) without the person having
// gone anywhere
private val OVERLAY_PACKAGES =
    setOf("android", SYSTEM_UI, "com.google.android.permissioncontroller", "com.android.permissioncontroller")

private const val STOPPED = "Stopped when you left the app, so it may not have finished. Nothing else was touched."

// how long another screen has to stay in front before it counts as the person having gone: phones draw their own
// things over an app for a moment as it opens (a transition, a floating bubble, a permission question), and that must
// not end a job that was working fine
private const val AWAY_GRACE_MS = 1_500L

// the person went somewhere else (home, another app, back to Krate) while the helper was working
private class Interrupted : Exception()

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

    // the job running now, and whether the helper has reached the app it's working in yet. After that, Krate's own
    // window coming up means the person came back, and the helper stops at once
    private var current: Job? = null
    private var reachedApp = false
    private var interrupted = false

    // something else in front, and for how long
    private val away = AwayTimer(AWAY_GRACE_MS)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> noteWindow(event.packageName?.toString())
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> noteMessage(event)
        }
    }

    private fun noteWindow(sender: String?) {
        if (sender == jobPackage) reachedApp = true
        if (reachedApp && sender == packageName && jobPackage != null) checkReturned()
    }

    // Krate's own window came up. A phone can report that in passing as the app opens, so it only ends the job when
    // Krate is still in front a moment later
    private fun checkReturned() {
        scope.launch {
            delay(AWAY_GRACE_MS)
            if (current?.isActive == true && rootInActiveWindow?.packageName?.toString() == packageName) {
                away.reset()
                interrupt()
            }
        }
    }

    // a notification carries its own data; a short on-screen message carries none. Android draws an app's short
    // messages in its system interface, which may be reported as their sender
    private fun noteMessage(event: AccessibilityEvent) {
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
        // a job cut off by the helper going away must not leave Krate saying it's still working
        if (current?.isActive == true) AutoBackupRunner.finish(AutoBackupState.Idle)
        scope.cancel()
        super.onDestroy()
    }

    // stops the job now: no more taps, no Back presses, and no pulling the person back to Krate
    internal fun interrupt() {
        if (current?.isActive != true) return
        interrupted = true
        current?.cancel()
    }

    internal fun run(job: AutoBackupJob) {
        interrupted = false
        reachedApp = false
        away.reset()
        current = scope.launch { runJob(job) }
    }

    private suspend fun runJob(job: AutoBackupJob) {
        val result = outcomeOf(job)
        jobPackage = null
        listenTo(packageName)
        // someone who has gone elsewhere is left where they are: no Back presses in their other app, no being pulled
        // back here
        if (interrupted) {
            AutoBackupRunner.finish(result)
            return
        }
        withContext(NonCancellable) { leave(job.watched) }
        AutoBackupRunner.finish(result)
        returnToKrate()
    }

    // how the job ended, whatever happened: done, or why not. An interruption is not an error; it's the person leaving
    private suspend fun outcomeOf(job: AutoBackupJob): AutoBackupState {
        KrateLog.event("helper start", "${job.mode} ${job.packageName} (${Build.MANUFACTURER} ${Build.MODEL})")
        val result = walked(job)
        val front = rootInActiveWindow?.packageName?.toString()
        KrateLog.event(outcomeLine(result), "${job.mode} ${job.packageName}${outcomeDetail(result)} (in front: $front)")
        return result
    }

    private fun outcomeLine(result: AutoBackupState) =
        if (result is AutoBackupState.Failed) "helper stopped" else "helper done"

    private fun outcomeDetail(result: AutoBackupState): String =
        when (result) {
            is AutoBackupState.Done -> " changed=${result.changed}"
            is AutoBackupState.Failed -> ": ${result.reason}"
            else -> ""
        }

    private suspend fun walked(job: AutoBackupJob): AutoBackupState {
        val stopped = { AutoBackupState.Failed(job.packageName, job.mode, stoppedText()) }
        return try {
            listenTo(job.watched)
            jobPackage = job.watched
            finished(job, withTimeout(JOB_TIMEOUT_MS) { walk(job) })
        } catch (_: TimeoutCancellationException) {
            AutoBackupState.Failed(job.packageName, job.mode, "That took too long, so Krate stopped.")
        } catch (_: Interrupted) {
            interrupted = true
            stopped()
        } catch (_: CancellationException) {
            stopped()
        } catch (missing: StepNotFound) {
            AutoBackupState.Failed(job.packageName, job.mode, "Couldn't find \"${missing.step}\" in the app.")
        } catch (failure: IllegalStateException) {
            AutoBackupState.Failed(job.packageName, job.mode, failure.message ?: "Something got in the way.")
        }
    }

    // what a finished walk leaves behind: the text saved, the picks remembered as applied, and the result to show
    private fun finished(
        job: AutoBackupJob,
        walked: WalkResult,
    ): AutoBackupState {
        val text = walked.text
        if (!text.isNullOrBlank()) AppBackups.save(this, job.packageName, text)
        if (job.mode == BackupMode.APPLY) job.revision?.let { RecommendedMarks.applied(this, job.packageName, it) }
        return AutoBackupState.Done(
            job.packageName,
            job.mode,
            nothingToSave = job.mode == BackupMode.SAVE && text.isNullOrBlank(),
            changed = walked.changed,
            appSaid = walked.appSaid,
        )
    }

    // first, any app holding an address wanted here lets go of it; then Android's "Open by default" page for the app,
    // the wanted addresses ticked in its "Add link" box, then Add. Only the addresses asked for are touched, and one
    // the page doesn't list (already on, or not the app's) is simply skipped
    private suspend fun openLinks(job: AutoBackupJob): WalkResult {
        if (job.hosts.isEmpty() && job.releases.isEmpty()) error("There were no links to switch on.")
        job.releases.forEach { owner -> releaseLinksOf(owner) }
        if (job.hosts.isEmpty()) return WalkResult(changed = job.releases.size)
        openOpenByDefault(job.packageName)
        tap(job.watched, UiTarget(text = "Add link"))
        delay(STEP_SETTLE_MS)
        val ticked = job.hosts.count { host -> tickHost(job.watched, host) }
        if (ticked == 0) error("Couldn't find those links on the page.")
        tap(job.watched, UiTarget(text = "Add"))
        delay(STEP_SETTLE_MS)
        return WalkResult(changed = ticked + job.releases.size)
    }

    private suspend fun openOpenByDefault(packageName: String) {
        val opened = runCatching { startActivity(OpenByDefault.intent(packageName)) }.isSuccess
        if (!opened) error("This phone has no Open by default page Krate can reach.")
        delay(LAUNCH_SETTLE_MS)
    }

    // an app that holds an address outranks one the person ticked it for, so its own links go to the browser first;
    // that is all of that app's links, which the page says before anything is done. Left as it is when already off
    private suspend fun releaseLinksOf(owner: String) {
        openOpenByDefault(owner)
        val page = waitFor(SETTINGS_PACKAGE) { root -> root.takeIf { it.linkSwitch() != null } }
        val standing = page?.linkSwitch() ?: error("Couldn't find the switch for ${labelOf(owner)}'s links.")
        if (standing !is LinkSwitch.On) return
        val clickable = standing.turnOff.clickableSelfOrAncestor()
        stillThere(SETTINGS_PACKAGE)
        if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) != true) {
            tapOnScreen(SETTINGS_PACKAGE, standing.turnOff)
        }
        // the switch takes a moment to slide over, so the page is looked at for a few seconds rather than once
        val off =
            waitFor(SETTINGS_PACKAGE, DIALOG_CHECK_MS) { root -> root.takeIf { it.linkSwitch() is LinkSwitch.Off } }
        if (off == null) error("${labelOf(owner)}'s links wouldn't switch off. Turn them off on its page.")
    }

    private fun labelOf(packageName: String): String =
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString() }
            .getOrDefault(packageName)

    // the row whose label starts with the address (a note naming the app that has it now can follow it), tapped
    private fun tickHost(
        watched: String,
        host: String,
    ): Boolean {
        val row =
            appWindows(watched)
                .flatMap { root ->
                    root.findAll { node ->
                        node.text
                            ?.toString()
                            ?.trim()
                            ?.lowercase()
                            ?.startsWith(host) == true
                    }
                }.firstOrNull() ?: return false
        val clickable = row.clickableSelfOrAncestor()
        if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) != true) tapOnScreen(watched, row)
        return true
    }

    // opens the link inside the app (and only that app), then taps the button it asks for. It worked when the button's
    // gone afterwards; a button still there means the app didn't take it
    private suspend fun confirmLink(job: AutoBackupJob): WalkResult {
        val link = job.link ?: error("There was no link to open.")
        val confirm = job.confirm ?: error("There was nothing to tap.")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(job.packageName)
        if (!runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) {
            error("This version of the app can't take that link. Update it first.")
        }
        delay(LAUNCH_SETTLE_MS)
        val target = UiTarget(text = confirm)
        // the app's own screen can carry a button with the same label as the one it asks about, so it's the count that
        // tells: one fewer once the question has been answered
        val before = appWindows(job.packageName).sumOf { root -> root.matchCount(target) }
        tap(job.packageName, target)
        delay(STEP_SETTLE_MS * 2)
        val after = appWindows(job.packageName).sumOf { root -> root.matchCount(target) }
        check(after < before) { "The app is still asking. Tap $confirm in it yourself." }
        return WalkResult()
    }

    // from the app's first screen to its backup box; the text read out, for a backup
    private suspend fun walk(job: AutoBackupJob): WalkResult =
        when (job.mode) {
            BackupMode.LINK -> confirmLink(job)
            BackupMode.OPEN_LINKS -> openLinks(job)
            else -> walkBackupBox(job)
        }

    // from the app's first screen to its backup box, for a backup, a restore or applying picks
    private suspend fun walkBackupBox(job: AutoBackupJob): WalkResult {
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
            // these two never come through the backup box
            BackupMode.LINK, BackupMode.OPEN_LINKS -> WalkResult()
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
        KrateLog.event(
            "helper read",
            "box ${own.length} chars, ${ownSettings.size} settings; picks ${pickedSettings.size}; " +
                "to change ${changed.size}",
        )
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
                stillThere(packageName)
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
        KrateLog.event("helper step", "${target.text ?: target.description} found in $packageName")
        val clickable = found.clickableSelfOrAncestor()
        stillThere(packageName)
        if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) != true) tapOnScreen(packageName, found)
    }

    // whether the person has left the app being worked in: another app, home, or Krate itself has been in front for a
    // moment. System questions over the app don't count, and neither does something passing over it
    private fun personLeft(watched: String): Boolean {
        val front = rootInActiveWindow?.packageName?.toString()
        // not before the helper has reached the app: Krate is in front until the app opens
        val elsewhere = reachedApp && front != null && front != watched && front !in OVERLAY_PACKAGES
        return away.away(elsewhere, SystemClock.uptimeMillis(), front.takeIf { it != packageName })
    }

    // why the job stopped, naming what came in front when it wasn't Krate
    private fun stoppedText(): String {
        val other = labelOf(away.who ?: return STOPPED)
        return "Stopped because $other came in front of the app, so it may not have finished. Nothing else was touched."
    }

    private fun stillThere(watched: String) {
        if (personLeft(watched)) throw Interrupted()
    }

    internal fun checkStillThere(watched: String) = stillThere(watched)

    // a tap on the node's own spot, only while the app is still in front: bounds from earlier would otherwise land on
    // whatever is under them now, which could be Krate's own screen
    private fun tapOnScreen(
        watched: String,
        node: AccessibilityNodeInfo,
    ) {
        // strictly, with no grace: a tap is the one thing that must never land on someone else's screen
        val front = rootInActiveWindow?.packageName?.toString()
        if (front != null && front != watched && front !in OVERLAY_PACKAGES) throw Interrupted()
        tapAt(node)
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
        (this as? KrateBackupService)?.checkStillThere(packageName)
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

// how many buttons or labels on screen carry the target's words
private fun AccessibilityNodeInfo.matchCount(target: UiTarget): Int =
    findAll { node ->
        val text = node.text?.toString()?.trim()
        val description = node.contentDescription?.toString()?.trim()
        (target.text != null && text.equals(target.text, ignoreCase = true)) ||
            (target.description != null && description.equals(target.description, ignoreCase = true))
    }.size

private fun AccessibilityNodeInfo.find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? =
    findAll(predicate).firstOrNull()

internal fun AccessibilityNodeInfo.findAll(predicate: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
    val found = mutableListOf<AccessibilityNodeInfo>()
    val queue = ArrayDeque(listOf(this))
    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        if (node.isVisibleToUser && predicate(node)) found += node
        for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
    }
    return found
}

internal fun AccessibilityNodeInfo.clickableSelfOrAncestor(): AccessibilityNodeInfo? {
    var node: AccessibilityNodeInfo? = this
    while (node != null && !node.isClickable) node = node.parent
    return node
}
