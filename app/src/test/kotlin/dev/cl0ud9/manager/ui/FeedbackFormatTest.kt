package dev.cl0ud9.manager.ui

import dev.cl0ud9.manager.domain.model.ActivityAction
import dev.cl0ud9.manager.domain.model.ActivityEntry
import dev.cl0ud9.manager.ui.apps.looksLikeLink
import dev.cl0ud9.manager.ui.settings.ReportedApp
import dev.cl0ud9.manager.ui.settings.feedbackIssue
import dev.cl0ud9.manager.ui.settings.feedbackShareText
import dev.cl0ud9.manager.ui.settings.formatDiagnosticReport
import dev.cl0ud9.manager.ui.settings.formatReportTime
import dev.cl0ud9.manager.ui.settings.reportedVersion
import dev.cl0ud9.manager.ui.util.MAX_ISSUE_URL_LENGTH
import dev.cl0ud9.manager.ui.util.githubNewIssueUrl
import dev.cl0ud9.manager.ui.util.issueTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.time.ZoneId

class FeedbackFormatTest {
    private val footer = "Krate 0.2.9, Android 15, Nothing A065"

    private fun query(
        url: String,
        name: String,
    ): String {
        val raw =
            url
                .substringAfter("?")
                .split("&")
                .first { it.startsWith("$name=") }
                .substringAfter("=")
        return URLDecoder.decode(raw, "UTF-8")
    }

    @Test
    fun `issue links keep spaces and new lines intact`() {
        val url = githubNewIssueUrl("App suggestion: Mihon", "App: Mihon\nLink: https://x.y/a?b=c&d")
        assertTrue(url.startsWith("https://github.com/Cl0ud-9/krate/issues/new?title="))
        assertFalse(url.contains("+"))
        assertEquals("App suggestion: Mihon", query(url, "title"))
        assertEquals("App: Mihon\nLink: https://x.y/a?b=c&d", query(url, "body"))
    }

    @Test
    fun `issue titles use the first line and stay short`() {
        assertEquals("Feedback", issueTitle("Feedback", "   \n "))
        assertEquals("Feedback: Download stuck", issueTitle("Feedback", "\n  Download stuck \nmore detail"))
        val long = issueTitle("Feedback", "x".repeat(200))
        assertTrue(long.endsWith("...") && long.length < 80)
    }

    @Test
    fun `feedback issue carries the message, footer and folded report`() {
        val issue = feedbackIssue("It crashed", "Krate diagnostic report\nApp: x", footer)
        val body = query(issue.url, "body")
        assertFalse(issue.reportOnClipboard)
        assertEquals("Feedback: It crashed", query(issue.url, "title"))
        assertTrue(body.startsWith("It crashed\n\n---\n$footer"))
        assertTrue(
            body.contains(
                "<details><summary>Diagnostic report</summary>\n\n```\n" +
                    "Krate diagnostic report\nApp: x\n```\n</details>",
            ),
        )
    }

    @Test
    fun `a report too long for a link moves to the clipboard`() {
        val issue = feedbackIssue("Too much", "y".repeat(MAX_ISSUE_URL_LENGTH), footer)
        assertTrue(issue.reportOnClipboard)
        assertTrue(issue.url.length <= MAX_ISSUE_URL_LENGTH)
        assertTrue(query(issue.url, "body").contains("copied to your clipboard"))
    }

    @Test
    fun `shared feedback always says where it came from`() {
        assertEquals("Hi\n\n---\n$footer", feedbackShareText(" Hi ", null, footer))
        assertEquals("Hi\n\nREPORT", feedbackShareText("Hi", "REPORT\n", footer))
    }

    @Test
    fun `report lists apps and activity with exact times`() {
        val utc = ZoneId.of("UTC")
        assertEquals("1970-01-01 00:00 UTC", formatReportTime(0, utc))
        val report =
            formatDiagnosticReport(
                deviceSummary = "App: dev.cl0ud9.manager 0.2.9 (build 17)\n",
                apps =
                    listOf(
                        ReportedApp("Mihon", "0.19.0", "0.20.4", "0.19.0"),
                        ReportedApp("Example Player", null, null, null),
                    ),
                recentActivity =
                    listOf(ActivityEntry("1", "mihon", "Mihon", ActivityAction.FAILED, 0, "Download: No internet")),
                hasGitHubToken = false,
                generatedAtMillis = 0,
            )
        assertTrue(report.contains("GitHub token saved: no"))
        assertTrue(report.contains("Apps (1 of 2 installed):"))
        assertTrue(report.contains("- Mihon: installed 0.19.0, latest 0.20.4"))
        assertTrue(report.contains("- Example Player: not installed, latest unknown"))
        assertTrue(report.contains("Mihon: failed - Download: No internet"))
        assertFalse(report.contains("ago"))
    }

    @Test
    fun `suggestion links need to look like a web address`() {
        listOf("github.com/mihonapp/mihon", "https://github.com/a/b", "http://f-droid.org", "mihon.app").forEach {
            assertTrue(it, looksLikeLink(it))
        }
        listOf(
            "",
            "mihon",
            "github com/a",
            "https://",
            ".com",
            "github.",
        ).forEach { assertFalse(it, looksLikeLink(it)) }
    }

    @Test
    fun `report versions read the same way as the installed ones`() {
        assertEquals("5.1.0", reportedVersion("5.1.0", "v5.1.0"))
        assertEquals("7.1.1", reportedVersion("7.1.1", null))
        assertEquals("20.40.45 (build yt-p6.2.1-ca1c83)", reportedVersion("20.40.45", "yt-p6.2.1-ca1c83"))
    }
}
