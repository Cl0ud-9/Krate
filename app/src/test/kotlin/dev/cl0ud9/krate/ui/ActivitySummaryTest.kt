package dev.cl0ud9.krate.ui

import dev.cl0ud9.krate.data.activity.DOWNLOAD_FAILURE_PREFIX
import dev.cl0ud9.krate.domain.model.ActivityAction
import dev.cl0ud9.krate.domain.model.ActivityEntry
import dev.cl0ud9.krate.ui.home.activitySummary
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivitySummaryTest {
    private fun failed(detail: String) =
        ActivityEntry(
            id = "1",
            appId = "a",
            appName = "App",
            action = ActivityAction.FAILED,
            timestampMillis = 0L,
            detail = detail,
        )

    @Test
    fun `a build failing the signature check says so and offers no retry`() {
        val entry =
            failed(
                "$DOWNLOAD_FAILURE_PREFIX The download isn't signed by the expected developer, so it wasn't " +
                    "installed for your safety.",
            )
        assertEquals("Turned away at Krate's checks.", activitySummary(entry, "App", canRetry = true))
    }

    @Test
    fun `a dropped download still offers a retry`() {
        val entry = failed("$DOWNLOAD_FAILURE_PREFIX The server sent an empty file. Try again.")
        assertEquals("Dropped it on the way over. Tap to retry.", activitySummary(entry, "App", canRetry = true))
    }
}
