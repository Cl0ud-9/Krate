package dev.cl0ud9.krate.platform.backup

import android.view.accessibility.AccessibilityNodeInfo

// where an app's links stand on its "Open by default" page: handed to the app (the switch is on) with the thing to tap
// to turn it off, or already off. Android has worn this page two ways: a radio pair, "In the app" and "In your
// browser", and a switch called "Open supported links"; both are read
internal sealed interface LinkSwitch {
    data object Off : LinkSwitch

    class On(
        val turnOff: AccessibilityNodeInfo,
    ) : LinkSwitch
}

private const val IN_BROWSER = "In your browser"
private const val IN_APP = "In the app"
private const val OPEN_SUPPORTED = "Open supported links"
private const val LOOK_UP_LEVELS = 3

// what the page says about its app's links, or null when neither wording is on screen (yet)
internal fun AccessibilityNodeInfo.linkSwitch(): LinkSwitch? = radioPair() ?: supportedLinksSwitch()

private fun AccessibilityNodeInfo.radioPair(): LinkSwitch? {
    val browser = labelled(IN_BROWSER) ?: return null
    val app = labelled(IN_APP)
    return when {
        browser.checked() == true -> LinkSwitch.Off
        app?.checked() == true -> LinkSwitch.On(browser)
        else -> null
    }
}

private fun AccessibilityNodeInfo.supportedLinksSwitch(): LinkSwitch? {
    val row = labelled(OPEN_SUPPORTED) ?: return null
    return when (row.checked()) {
        true -> LinkSwitch.On(row)
        false -> LinkSwitch.Off
        null -> null
    }
}

private fun AccessibilityNodeInfo.labelled(text: String): AccessibilityNodeInfo? =
    findAll {
        it.text
            ?.toString()
            ?.trim()
            .equals(text, ignoreCase = true)
    }.firstOrNull()

// the row's own checked state, or its switch's or radio button's, in it or just around it; null when none can be found
private fun AccessibilityNodeInfo.checked(): Boolean? =
    generateSequence(this) { it.parent }
        .take(LOOK_UP_LEVELS)
        .firstNotNullOfOrNull { level ->
            level.takeIf { it.isCheckable }
                ?: level.findAll { it.isCheckable }.firstOrNull()
        }?.isChecked
