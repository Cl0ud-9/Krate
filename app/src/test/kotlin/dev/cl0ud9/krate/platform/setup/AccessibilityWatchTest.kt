package dev.cl0ud9.krate.platform.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityWatchTest {
    private val cleaner =
        WatchedService("com.cleaner/com.cleaner.Svc", "App Cache Cleaner", own = false, packageName = "com.cleaner")
    private val helper =
        WatchedService("dev.cl0ud9.krate/.Backup", "Krate's helper", own = true, packageName = "dev.cl0ud9.krate")

    @Test
    fun aShortClassNameMeansTheSameServiceAsTheFullOne() {
        assertEquals("com.cleaner/com.cleaner.Svc", serviceKey("com.cleaner/.Svc"))
        assertEquals("com.cleaner/com.cleaner.Svc", serviceKey("com.cleaner/com.cleaner.Svc"))
    }

    @Test
    fun malformedEntriesAreIgnored() {
        assertNull(serviceKey(""))
        assertNull(serviceKey("nothing"))
        assertNull(serviceKey("/.Svc"))
        assertNull(serviceKey("com.cleaner/"))
        assertEquals(emptySet<String>(), enabledServiceKeys(null))
        assertEquals(emptySet<String>(), enabledServiceKeys(""))
        assertEquals(setOf("com.cleaner/com.cleaner.Svc"), enabledServiceKeys(":com.cleaner/.Svc::junk:"))
    }

    @Test
    fun onlyWatchedServicesThatAreOnAreReported() {
        // a person's own assistive tool is on too, and is never reported
        val enabled = enabledServiceKeys("com.google.android.marvin.talkback/.TalkBackService:com.cleaner/.Svc")
        assertEquals(listOf(cleaner), servicesLeftOn(enabled, listOf(cleaner, helper), emptySet()))
    }

    @Test
    fun nothingIsReportedWhenNothingWatchedIsOn() {
        val talkBack = enabledServiceKeys("com.google.android.marvin.talkback/.TalkBackService")
        assertTrue(servicesLeftOn(talkBack, listOf(cleaner, helper), emptySet()).isEmpty())
        assertTrue(servicesLeftOn(emptySet(), listOf(cleaner, helper), emptySet()).isEmpty())
    }

    @Test
    fun aServiceKeptOnIsLeftAlone() {
        val enabled = setOf(cleaner.key, helper.key)
        assertEquals(listOf(helper), servicesLeftOn(enabled, listOf(cleaner, helper), setOf(cleaner.key)))
    }

    @Test
    fun aKeptServiceIsAskedAboutAgainOnceItHasBeenOffAndOnAgain() {
        // on and kept: remembered; switched off: forgotten, so the next time it's left on it is reported again
        assertEquals(setOf(cleaner.key), keptWhileOn(setOf(cleaner.key), setOf(cleaner.key)))
        assertEquals(emptySet<String>(), keptWhileOn(emptySet(), setOf(cleaner.key)))
    }
}
