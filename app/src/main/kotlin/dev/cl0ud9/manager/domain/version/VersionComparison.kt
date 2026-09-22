package dev.cl0ud9.manager.domain.version

// dot-separated numeric comparison (1.4.10 vs 1.4.9), tolerant of a non-numeric suffix on any one
// segment (7.2.1-dev.2 reads as 7.2.1.2 - the leading digit run of "1-dev" is still 1, not dropped
// entirely the way a plain toIntOrNull() would). That tolerance matters here: a package can gain a
// version like that entirely outside this app (MicroG RE's own in-app "hide icon" toggle installs
// its own beta build, for example) - a shared function every version comparison in this app goes
// through, not a private detail of the manager's own self-update check it started as, since the
// exact same "is X actually newer than Y" question applies to catalog apps too. Confirmed live: a
// naive != comparison flagged that beta as "update available" against an OLDER catalog release,
// which would have downgraded it had Update been tapped
fun isNewerVersion(
    latest: String,
    installed: String,
): Boolean {
    val latestParts = latest.split(".").map { it.leadingIntOrNull() }
    val installedParts = installed.split(".").map { it.leadingIntOrNull() }
    return if (latestParts.none { it != null } || installedParts.none { it != null }) {
        // neither side parsed a single numeric segment - nothing sensible to compare numerically,
        // fall back to plain inequality rather than silently treating every tag as equal
        latest != installed
    } else {
        compareVersionSegments(latestParts, installedParts) > 0
    }
}

private fun String.leadingIntOrNull(): Int? = takeWhile { it.isDigit() }.toIntOrNull()

private fun compareVersionSegments(
    latestParts: List<Int?>,
    installedParts: List<Int?>,
): Int {
    val length = maxOf(latestParts.size, installedParts.size)
    for (index in 0 until length) {
        val latestSegment = latestParts.getOrElse(index) { 0 } ?: 0
        val installedSegment = installedParts.getOrElse(index) { 0 } ?: 0
        val comparison = latestSegment.compareTo(installedSegment)
        if (comparison != 0) return comparison
    }
    return 0
}
