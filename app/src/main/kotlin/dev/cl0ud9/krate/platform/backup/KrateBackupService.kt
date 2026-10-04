package dev.cl0ud9.krate.platform.backup

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.cl0ud9.krate.KrateActivity
import dev.cl0ud9.krate.domain.model.UiTarget
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
private const val TAP_MS = 60L
private const val DIALOG_CHECK_MS = 3_000L
private const val FULL_LOOK_MS = FIND_TIMEOUT_MS + MAX_SCROLLS * SCROLL_AFTER_MS

// notices an app can stack up as it opens, each closed with Back
private const val MAX_DIALOGS = 3

// a step that couldn't be found, named for the message shown afterwards
private class StepNotFound(
    val step: String,
) : Exception()

// Krate's accessibility helper: when asked, it opens an app, taps its way to that app's own backup box, and reads
// the settings out of it or pastes them back in. It does nothing in between, and only looks at the app it's for
class KrateBackupService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onServiceConnected() {
        running = this
        listenTo(packageName)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

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
                    val text = walk(job)
                    if (!text.isNullOrBlank()) AppBackups.save(this@KrateBackupService, job.packageName, text)
                    AutoBackupState.Done(
                        job.packageName,
                        job.mode,
                        nothingToSave =
                            job.mode == BackupMode.SAVE && text.isNullOrBlank(),
                    )
                } catch (missing: StepNotFound) {
                    AutoBackupState.Failed(job.packageName, job.mode, "Couldn't find \"${missing.step}\" in the app.")
                } catch (failure: IllegalStateException) {
                    AutoBackupState.Failed(job.packageName, job.mode, failure.message ?: "Something got in the way.")
                }
            leave(job.packageName)
            listenTo(packageName)
            AutoBackupRunner.finish(result)
            returnToKrate()
        }
    }

    // from the app's first screen to its backup box; the text read out, for a backup
    private suspend fun walk(job: AutoBackupJob): String? {
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
        return when (job.mode) {
            BackupMode.SAVE -> {
                // an app with nothing changed from its defaults exports nothing, which isn't a failure
                val text = field.text?.toString().orEmpty()
                tap(job.packageName, UiTarget(text = job.spec.close))
                text
            }
            BackupMode.RESTORE -> {
                val arguments =
                    Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, job.text)
                    }
                if (!field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
                    error("Couldn't paste into the backup box.")
                }
                delay(STEP_SETTLE_MS)
                tap(job.packageName, UiTarget(text = job.spec.apply))
                null
            }
        }
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

    // backs out of the app's screens it opened, so it's left where it started
    private suspend fun leave(packageName: String) {
        repeat(MAX_BACKS) {
            if (appWindows(packageName).isEmpty() || rootInActiveWindow?.packageName?.toString() != packageName) return
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(POLL_MS * 2)
        }
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
        info.packageNames = arrayOf(packageName, this.packageName)
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
