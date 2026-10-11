package dev.cl0ud9.krate.ui.details

import dev.cl0ud9.krate.domain.links.LinkState
import dev.cl0ud9.krate.domain.links.missing

// where an app's links stand right now: the addresses it should open, and which of them already do. Re-read whenever
// Krate comes back from Android's settings, so a switch turned there shows at once
internal class LinkStanding(
    val wanted: List<String>,
    val states: List<LinkState>,
) {
    val todo: List<String> get() = missing(wanted, states)
    val readable: Boolean get() = states.isNotEmpty()
}
