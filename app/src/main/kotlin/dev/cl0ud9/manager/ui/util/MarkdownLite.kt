package dev.cl0ud9.manager.ui.util

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

private typealias Builder = AnnotatedString.Builder

// GitHub release bodies are markdown (release notes shown on AppDetailsScreen come straight from
// GitHub releases, section 9/42.9 of the spec), but the app has no full markdown renderer - a real
// engine is overkill for what actually shows up in practice. This cleans up the constructs that do
// show up: **bold** spans, `code` spans, [text](url) links, bare https:// URLs (GitHub renders
// those as clickable too, and plenty of release notes rely on that instead of bracket syntax),
// +/-/* bullet lists, and #/##/### ATX headers, so raw "**"/"`"/"[...](...)"/"+ "/"### " syntax
// doesn't leak into the UI.
private val HEADER_LINE = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET_LINE = Regex("^(\\s*)[+*-]\\s(.*)$")
private val HEADER_FONT_SIZE = 15.sp

// bundles the theme colors inline spans need into one, purely to keep appendWithInlineSpans
// under detekt's parameter-count threshold without losing each color's own name at the call site
private data class InlineSpanColors(
    val body: Color,
    val link: Color,
    val codeText: Color,
    val codeBackground: Color,
)

@Composable
fun String.formatMarkdownLite(): AnnotatedString {
    val headerColor = MaterialTheme.colorScheme.primary
    val colors =
        InlineSpanColors(
            body = LocalContentColor.current,
            link = MaterialTheme.colorScheme.primary,
            codeText = MaterialTheme.colorScheme.onSurfaceVariant,
            codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    return buildAnnotatedString {
        val lines = trimEnd().lines()
        var previousWasBlank = true // no separating blank line needed before the very first line
        lines.forEachIndexed { index, line ->
            if (index > 0) append('\n')
            val header = HEADER_LINE.matchEntire(line)
            val bullet = BULLET_LINE.matchEntire(line)
            when {
                header != null -> {
                    val (_, text) = header.destructured
                    // an extra blank line ahead of a header separates sections visually, matching
                    // how a real markdown renderer would space a heading from the paragraph before it
                    if (!previousWasBlank) append('\n')
                    val headerStyle =
                        SpanStyle(fontWeight = FontWeight.Bold, fontSize = HEADER_FONT_SIZE, color = headerColor)
                    withStyle(headerStyle) {
                        appendWithInlineSpans(text.trim(), colors)
                    }
                }

                // no ParagraphStyle/hanging-indent here on purpose - wrapping each bullet in its
                // own paragraph looked correct in isolation but made Compose insert noticeably
                // larger gaps between consecutive bullets than a plain '\n' does (confirmed live:
                // a real multi-bullet changelog rendered with huge vertical gaps between every
                // line). A wrapped continuation line falling back to the left margin is a smaller
                // cosmetic issue than that.
                bullet != null -> {
                    val (indent, rest) = bullet.destructured
                    append(indent)
                    append(if (indent.isEmpty()) "•  " else "◦  ")
                    appendWithInlineSpans(rest, colors)
                }

                else -> appendWithInlineSpans(line, colors)
            }
            previousWasBlank = line.isBlank()
        }
    }
}

// the position (if any) of the next occurrence of a two-sided inline marker, or -1 if it isn't
// present or has no matching closing marker
private fun String.nextMarkerStart(marker: String): Int {
    val start = indexOf(marker)
    return if (start != -1 && indexOf(marker, start + marker.length) != -1) start else -1
}

// **bold**, `code`, [text](url) links, and bare https:// URLs, processed together in one
// left-to-right pass since any of them can appear first within the same line
private fun Builder.appendWithInlineSpans(
    text: String,
    colors: InlineSpanColors,
) {
    var remaining = text
    while (remaining.isNotEmpty()) {
        val boldStart = remaining.nextMarkerStart("**")
        val codeStart = remaining.nextMarkerStart("`")
        val linkMatch = LINK_SYNTAX.find(remaining)
        val linkStart = linkMatch?.range?.first ?: -1
        // a bare URL inside an already-matched [text](url) link never wins: the bracket match's
        // own start is always earlier than its embedded URL, so the link branch below consumes
        // the whole thing atomically before a bare-URL match on that same text is ever considered
        val bareUrlMatch = BARE_URL.find(remaining)
        val bareUrlStart = bareUrlMatch?.range?.first ?: -1

        val starts = listOf(boldStart, codeStart, linkStart, bareUrlStart).filter { it != -1 }
        val earliest = starts.minOrNull()
        when {
            earliest == null -> {
                append(remaining)
                remaining = ""
            }

            earliest == linkStart -> {
                val match = linkMatch!!
                append(remaining.substring(0, match.range.first))
                val linkStyle = TextLinkStyles(style = SpanStyle(color = colors.link))
                withLink(LinkAnnotation.Url(match.groupValues[2], linkStyle)) {
                    append(match.groupValues[1])
                }
                remaining = remaining.substring(match.range.last + 1)
            }

            earliest == bareUrlStart -> {
                val match = bareUrlMatch!!
                append(remaining.substring(0, match.range.first))
                val linkStyle = TextLinkStyles(style = SpanStyle(color = colors.link))
                withLink(LinkAnnotation.Url(match.value, linkStyle)) {
                    append(shortLinkText(match.value))
                }
                remaining = remaining.substring(match.range.last + 1)
            }

            earliest == codeStart -> {
                val end = remaining.indexOf("`", codeStart + 1)
                append(remaining.substring(0, codeStart))
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = colors.codeText,
                        background = colors.codeBackground,
                    ),
                ) {
                    append(remaining.substring(codeStart + 1, end))
                }
                remaining = remaining.substring(end + 1)
            }

            else -> {
                val end = remaining.indexOf("**", boldStart + 2)
                append(remaining.substring(0, boldStart))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.body)) {
                    append(remaining.substring(boldStart + 2, end))
                }
                remaining = remaining.substring(end + 2)
            }
        }
    }
}

private val LINK_SYNTAX = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")

// excludes a trailing ')' from the match so "(see https://example.com/x)" doesn't swallow the
// closing paren into the link - the same heuristic GitHub's own bare-URL autolinker uses
private val BARE_URL = Regex("https?://[^\\s)]+")

private val GITHUB_COMMIT_URL = Regex("^https://github\\.com/[^/]+/[^/]+/commit/([0-9a-f]{7})[0-9a-f]*$")
private val GITHUB_NUMBERED_URL = Regex("^https://github\\.com/[^/]+/[^/]+/(?:pull|issues)/(\\d+)$")

// a full commit or pull request URL in release notes is noise for a reader - GitHub itself shows
// these as "a1b2c3d" and "#123", so do the same while keeping them tappable
private fun shortLinkText(url: String): String =
    GITHUB_COMMIT_URL.matchEntire(url)?.let { "commit ${it.groupValues[1]}" }
        ?: GITHUB_NUMBERED_URL.matchEntire(url)?.let { "#${it.groupValues[1]}" }
        ?: url
