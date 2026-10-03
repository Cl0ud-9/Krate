package dev.cl0ud9.krate.ui.details

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// installs asked for from outside the app's page (a download notification's Install button); the page acts on its
// own app's request once it's ready, and takes it off the list as it does
object InstallRequests {
    private val mutableRequested = MutableStateFlow<Set<String>>(emptySet())
    val requested: StateFlow<Set<String>> = mutableRequested.asStateFlow()

    fun request(appId: String) {
        mutableRequested.update { it + appId }
    }

    // true once, for the caller that gets to act on it
    fun consume(appId: String): Boolean {
        var had = false
        mutableRequested.update { current ->
            had = appId in current
            current - appId
        }
        return had
    }
}
