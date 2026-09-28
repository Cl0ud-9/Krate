package dev.cl0ud9.krate.ui.settings

import dev.cl0ud9.krate.domain.model.InviteStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubAccessSummaryTest {
    private val statuses =
        listOf(
            InviteStatus.NoToken,
            InviteStatus.Unchecked,
            InviteStatus.Checking,
            InviteStatus.Open(0),
            InviteStatus.Open(1),
            InviteStatus.Open(3),
            InviteStatus.Rejected,
            InviteStatus.Unreachable,
        )

    @Test
    fun noTokenAlwaysInvitesYouToAddOne() {
        statuses.forEach { status ->
            assertEquals("Unlocks invite-only apps", gitHubAccessSummary(false, status, playful = true))
            assertEquals("Unlocks invite-only apps", gitHubAccessSummary(false, status, playful = false))
        }
    }

    @Test
    fun unlockedAppsAreCountedInBothVoices() {
        assertEquals("You're in. 1 invite-only app unlocked", gitHubAccessSummary(true, InviteStatus.Open(1), true))
        assertEquals("3 invite-only apps unlocked", gitHubAccessSummary(true, InviteStatus.Open(3), false))
    }

    @Test
    fun aRejectedTokenIsNeverDescribedAsWorking() {
        listOf(true, false).forEach { playful ->
            val line = gitHubAccessSummary(true, InviteStatus.Rejected, playful)
            assertTrue(line, "unlocked" !in line && "You're in" !in line)
        }
    }

    @Test
    fun linesStayShortEnoughForOneRow() {
        statuses.forEach { status ->
            listOf(true, false).forEach { playful ->
                val line = gitHubAccessSummary(true, status, playful)
                assertTrue(line, line.length <= 48)
            }
        }
    }
}
