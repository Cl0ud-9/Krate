package dev.cl0ud9.krate.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import kotlin.math.abs

private const val SWIPES_PER_ITERATION = 3
private const val FIND_MS = 5_000L
private const val SCROLL_TRIES = 6
private const val SETTINGS_TRIES = 3
private const val SCROLL_FROM = 0.75f
private const val SCROLL_TO = 0.35f
private const val SCROLL_STEPS = 20

// a real edge swipe, slow enough for the system to treat it as a back gesture and play it frame by frame
private const val BACK_SWIPE_STEPS = 40
private const val BACK_SWIPE_EDGE_X = 2
private const val BACK_SWIPE_END_FRACTION = 0.8f

// the steps SmoothnessBenchmark's journeys are made of
internal fun MacrobenchmarkScope.openSettings() {
    clickDescription("Home")
    // a tab tap right after a page has closed can be lost, so it's tried again until Settings shows
    repeat(SETTINGS_TRIES) {
        clickDescription("Settings")
        if (device.wait(Until.hasObject(By.text("Appearance")), FIND_MS)) return
    }
    error("Settings didn't open")
}

internal fun MacrobenchmarkScope.openAndSwipeBack(open: MacrobenchmarkScope.() -> Unit) {
    repeat(SWIPES_PER_ITERATION) {
        open()
        waitForUi(SETTLE_MS)
        val y = device.displayHeight / 2
        device.swipe(
            BACK_SWIPE_EDGE_X,
            y,
            (device.displayWidth * BACK_SWIPE_END_FRACTION).toInt(),
            y,
            BACK_SWIPE_STEPS,
        )
        waitForUi(SETTLE_MS)
    }
}

// liquid glass is a setting that sticks, so every run puts it where its test wants it
internal fun MacrobenchmarkScope.setGlass(on: Boolean) {
    openSettings()
    clickText("Appearance")
    val label = findByScrolling("Liquid glass") ?: error("No Liquid glass setting on the Appearance page")
    val labelY = label.visibleBounds.centerY()
    // the switch row holds the label, or failing that it's the switch level with it
    val switch =
        device.findObject(By.checkable(true).hasDescendant(By.text("Liquid glass")))
            ?: device.findObjects(By.checkable(true)).minByOrNull { abs(it.visibleBounds.centerY() - labelY) }
            ?: error("No switch next to Liquid glass")
    if (switch.isChecked != on) {
        label.click()
        waitForUi()
    }
    device.pressBack()
    waitForUi()
    device.pressBack()
    waitForUi()
}

// scrolls the page down a screen at a time until the text shows up
private fun MacrobenchmarkScope.findByScrolling(text: String): UiObject2? {
    repeat(SCROLL_TRIES) {
        device.wait(Until.findObject(By.text(text)), FIND_MS / SCROLL_TRIES)?.let { return it }
        val x = device.displayWidth / 2
        device.swipe(
            x,
            (device.displayHeight * SCROLL_FROM).toInt(),
            x,
            (device.displayHeight * SCROLL_TO).toInt(),
            SCROLL_STEPS,
        )
        waitForUi()
    }
    return device.findObject(By.text(text))
}
