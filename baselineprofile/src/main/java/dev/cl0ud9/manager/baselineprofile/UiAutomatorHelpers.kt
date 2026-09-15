package dev.cl0ud9.manager.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

private const val FIND_TIMEOUT_MS = 5_000L
private const val UI_SETTLE_DELAY_MS = 500L
private const val UI_IDLE_TIMEOUT_MS = 2_000L
private const val SWIPE_STEPS = 30
private const val CLICK_ATTEMPTS = 3

// a swipe fraction of screen height, not fixed pixels, so this reads the same across the range of
// screens the connected device(s) generating this profile might be
private const val SWIPE_START_FRACTION = 0.8f
private const val SWIPE_END_FRACTION = 0.3f
private const val FIRST_LIST_ITEM_Y_FRACTION = 0.32f

internal fun MacrobenchmarkScope.waitForUi(delayMs: Long = UI_SETTLE_DELAY_MS) {
    device.waitForIdle(UI_IDLE_TIMEOUT_MS)
    Thread.sleep(delayMs)
}

private fun MacrobenchmarkScope.findBy(selector: BySelector): UiObject2 =
    device.wait(Until.findObject(selector), FIND_TIMEOUT_MS)
        ?: error("Could not find a UI element matching $selector")

// re-finds the element fresh on every attempt rather than clicking a UiObject2 handed in from
// earlier - the header/list this app scrolls before most clicks can still be mid-collapse-animation
// right when a lookup succeeds, and by the time visibleCenter is read off that same reference a
// moment later the underlying node has already changed, throwing StaleObjectException
private fun MacrobenchmarkScope.clickWhenFound(selector: BySelector) {
    var lastFailure: StaleObjectException? = null
    repeat(CLICK_ATTEMPTS) {
        try {
            val center = findBy(selector).visibleCenter
            device.click(center.x, center.y)
            return
        } catch (stale: StaleObjectException) {
            lastFailure = stale
            waitForUi()
        }
    }
    throw checkNotNull(lastFailure) { "clickWhenFound retries exhausted without a StaleObjectException" }
}

internal fun MacrobenchmarkScope.clickDescription(label: String) {
    clickWhenFound(By.desc(label))
    waitForUi()
}

internal fun MacrobenchmarkScope.clickText(label: String) {
    clickWhenFound(By.text(label))
    waitForUi()
}

internal fun MacrobenchmarkScope.openFirstListItem() {
    device.click(device.displayWidth / 2, (device.displayHeight * FIRST_LIST_ITEM_Y_FRACTION).toInt())
    waitForUi()
}

// well past the natural end on both sides, so the platform's own edge-of-list overscroll gets
// exercised too, not just plain in-bounds scrolling
internal fun MacrobenchmarkScope.scrollDownAndUp() {
    val midX = device.displayWidth / 2
    val bottomY = (device.displayHeight * SWIPE_START_FRACTION).toInt()
    val topY = (device.displayHeight * SWIPE_END_FRACTION).toInt()
    repeat(2) {
        device.swipe(midX, bottomY, midX, topY, SWIPE_STEPS)
        waitForUi()
    }
    repeat(2) {
        device.swipe(midX, topY, midX, bottomY, SWIPE_STEPS)
        waitForUi()
    }
}
