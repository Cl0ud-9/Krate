package dev.cl0ud9.krate.ui.details

import dev.cl0ud9.krate.domain.links.LinkState
import dev.cl0ud9.krate.domain.links.missing

// where an app's links stand right now: the addresses it should open, and which of them already do. Re-read whenever
// Krate comes back from Android's settings, so a switch turned there shows at once
internal class LinkStanding(
    val wanted: List<String>,
    val states: List<LinkState>,
    // an address to the app that opens it now, when that's another app: a verified owner outranks a switch ticked for
    // this one, so ticking alone changes nothing until the owner lets go
    val owners: Map<String, String> = emptyMap(),
    // package to the name the person knows it by
    val ownerNames: Map<String, String> = emptyMap(),
) {
    // ticked off in Android's list for this app already, or not
    private val unticked: List<String> get() = missing(wanted, states)

    // what's still to do: addresses not switched on, and ones switched on that another app keeps anyway
    val todo: List<String> get() = wanted.filter { it in unticked || it in owners }

    // the ones that still need ticking for this app; the rest only need their owner to let go
    val toTick: List<String> get() = unticked

    // the apps that hold an address wanted here, whose own links have to be switched off first
    val releases: List<String> get() = todo.mapNotNull(owners::get).distinct()

    val readable: Boolean get() = states.isNotEmpty()

    fun ownerName(host: String): String? = owners[host]?.let { ownerNames[it] ?: it }

    // the names of the apps that would be asked to let go, for the sentence that says so
    fun releaseNames(): List<String> = releases.map { ownerNames[it] ?: it }
}
