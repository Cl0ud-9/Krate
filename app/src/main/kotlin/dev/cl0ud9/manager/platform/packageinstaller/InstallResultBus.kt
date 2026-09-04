package dev.cl0ud9.manager.platform.packageinstaller

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class InstallResultEvent(
    // "install:<sessionId>" for an install session, "uninstall:<packageName>" for an uninstall request
    val requestKey: String,
    val status: Int,
    val message: String?,
)

// relays PackageInstaller broadcast results from InstallResultReceiver to whichever engine flow is
// waiting on that request, since the broadcast arrives out of band from the install/uninstall call itself
object InstallResultBus {
    private val mutableEvents = MutableSharedFlow<InstallResultEvent>(extraBufferCapacity = BUFFER_CAPACITY)
    val events: SharedFlow<InstallResultEvent> = mutableEvents.asSharedFlow()

    fun emit(event: InstallResultEvent) {
        mutableEvents.tryEmit(event)
    }

    private const val BUFFER_CAPACITY = 8
}
