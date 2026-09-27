package dev.cl0ud9.krate.ui.util

import java.net.URLEncoder

const val KRATE_REPO_URL = "https://github.com/Cl0ud-9/Krate"
const val KOFI_URL = "https://ko-fi.com/cl0ud9"

// browsers and GitHub start refusing links much past this, so longer content has to travel another way
const val MAX_ISSUE_URL_LENGTH = 7000

private const val TITLE_MAX_CHARS = 60

// a pre-filled GitHub "new issue" link; spaces become %20, which every browser and GitHub read the same way
fun githubNewIssueUrl(
    title: String,
    body: String,
    repoUrl: String = KRATE_REPO_URL,
): String = "$repoUrl/issues/new?title=${encodeQuery(title)}&body=${encodeQuery(body)}"

fun encodeQuery(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

// "Prefix: first line of what they wrote", trimmed, so issues can be told apart at a glance
fun issueTitle(
    prefix: String,
    text: String,
): String {
    val firstLine = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return prefix
    val short = if (firstLine.length > TITLE_MAX_CHARS) firstLine.take(TITLE_MAX_CHARS).trimEnd() + "..." else firstLine
    return "$prefix: $short"
}
