package dev.cl0ud9.krate

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference

private const val SKIP_SETUP_EXTRA = "is_benchmark"
private const val RECREATIONS = 6
private const val GIVE_UP_MS = 30_000L
private const val GC_PAUSE_MS = 400L

// the test framework itself keeps one recently replaced screen for a while; a leak holds every one of them
private const val TEST_FRAMEWORK_HOLDS = 1

// a screen is recreated whenever the phone rotates or its font or display size changes; each old one must be freed, not kept
// alive by something that outlives it (it once was, through the system's night-mode manager asked for via the Activity)
@RunWith(AndroidJUnit4::class)
class ActivityLeakTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun recreatedScreensAreFreed() {
        val earlier = mutableListOf<WeakReference<Activity>>()
        val intent = Intent(context, KrateActivity::class.java).putExtra(SKIP_SETUP_EXTRA, true)
        ActivityScenario.launch<KrateActivity>(intent).use { scenario ->
            repeat(RECREATIONS) {
                scenario.onActivity { earlier += WeakReference(it) }
                scenario.recreate()
            }
            val deadline = SystemClock.uptimeMillis() + GIVE_UP_MS
            var held = earlier.count { it.get() != null }
            while (held > TEST_FRAMEWORK_HOLDS && SystemClock.uptimeMillis() < deadline) {
                Runtime.getRuntime().gc()
                System.runFinalization()
                Thread.sleep(GC_PAUSE_MS)
                held = earlier.count { it.get() != null }
            }
            val which = earlier.indices.filter { earlier[it].get() != null }
            val message = "screens still held after being replaced: $which of ${earlier.size}"
            assertTrue(message, held <= TEST_FRAMEWORK_HOLDS)
        }
    }
}
