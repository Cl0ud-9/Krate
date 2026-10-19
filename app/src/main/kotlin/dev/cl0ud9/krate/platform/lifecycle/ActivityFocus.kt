package dev.cl0ud9.krate.platform.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// whether one of Krate's own screens is the one in front right now. Unlike the process lifecycle, which waits most of a
// second before it says the app has lost the front, this changes the moment a screen is paused, so a system dialog
// that opens and is cancelled straight away is still seen opening and closing
object ActivityFocus {
    private val resumed = MutableStateFlow(0)
    private val front = MutableStateFlow(false)
    val inFront: StateFlow<Boolean> = front.asStateFlow()

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) = change(+1)

                override fun onActivityPaused(activity: Activity) = change(-1)

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    private fun change(by: Int) {
        resumed.value = (resumed.value + by).coerceAtLeast(0)
        front.value = resumed.value > 0
    }
}
