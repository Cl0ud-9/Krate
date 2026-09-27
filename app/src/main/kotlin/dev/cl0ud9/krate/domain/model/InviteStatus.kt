package dev.cl0ud9.krate.domain.model

// what the saved GitHub token got from the invite-only catalog the last time Krate asked
sealed interface InviteStatus {
    data object NoToken : InviteStatus

    // a token is saved but hasn't been tried yet this run
    data object Unchecked : InviteStatus

    data object Checking : InviteStatus

    data class Open(
        val appCount: Int,
    ) : InviteStatus

    // GitHub turned the token away, or it can't see the invite-only repo
    data object Rejected : InviteStatus

    // offline, GitHub trouble, or a catalog that didn't verify; the last good copy stays in use
    data object Unreachable : InviteStatus
}
