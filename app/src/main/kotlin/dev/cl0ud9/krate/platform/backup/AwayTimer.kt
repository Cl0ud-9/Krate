package dev.cl0ud9.krate.platform.backup

// whether something else has been in front for long enough to count as the person having gone: phones draw their own
// things over an app for a moment as it opens (a transition, a floating bubble, a permission question), and that must
// not end a job that was working fine
internal class AwayTimer(
    private val graceMs: Long,
) {
    private var since = 0L

    // what has been in front, for the message if the job stops for it
    var who: String? = null
        private set

    // true once the same thing has been in front for the whole grace period; coming back (or nothing odd in front)
    // starts the count over
    fun away(
        isAway: Boolean,
        now: Long,
        front: String?,
    ): Boolean {
        if (!isAway) {
            reset()
            return false
        }
        if (since == 0L) {
            since = now
            who = front
        }
        return now - since >= graceMs
    }

    fun reset() {
        since = 0L
        who = null
    }
}
