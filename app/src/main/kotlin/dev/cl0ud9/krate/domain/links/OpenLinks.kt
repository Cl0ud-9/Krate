package dev.cl0ud9.krate.domain.links

// the web addresses an app is meant to open, from the catalog: the ones it always claims, and ones it only claims while
// the app named beside them isn't installed (so two apps that can both open an address never fight over it)
data class OpenLinks(
    val hosts: List<String>,
    val unlessInstalled: Map<String, String> = emptyMap(),
)

// whether one address is switched on for an app
data class LinkState(
    val host: String,
    val on: Boolean,
)

// the addresses to switch on for an app: its own, plus the ones nobody better placed has; and, of those, only the ones
// the app can actually open (a catalog entry never reaches past what the app declares)
fun wantedHosts(
    links: OpenLinks,
    installed: Set<String>,
    declared: Set<String>,
): List<String> {
    val shared = links.unlessInstalled.filterValues { it !in installed }.keys
    return (links.hosts + shared).map { it.lowercase() }.distinct().filter { it in declared }
}

// what's left to switch on, given what's already on
fun missing(
    wanted: List<String>,
    states: List<LinkState>,
): List<String> {
    val on = states.filter { it.on }.mapTo(mutableSetOf()) { it.host.lowercase() }
    return wanted.filter { it !in on }
}

// for an app with no catalog entry (one someone tracks themselves): the addresses it declares that nobody else has
// verified. Wildcards are left out, and a long list is more than a person should be asked to switch on at a stroke
fun declaredHosts(
    declared: List<String>,
    takenByAnother: (String) -> Boolean,
    limit: Int = MAX_TRACKED_HOSTS,
): List<String> =
    declared
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() && '*' !in it && it.contains('.') }
        .distinct()
        .filterNot(takenByAnother)
        .take(limit)

const val MAX_TRACKED_HOSTS = 6
