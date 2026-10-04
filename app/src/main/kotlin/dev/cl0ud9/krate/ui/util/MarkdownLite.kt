package dev.cl0ud9.krate.ui.util

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

private typealias Builder = AnnotatedString.Builder

// GitHub release bodies are markdown (release notes shown on AppDetailsScreen come straight from
// GitHub releases, section 9/42.9 of the spec), but the app has no full markdown renderer - a real
// engine is overkill for what actually shows up in practice. This cleans up the constructs that do
// show up: **bold** spans, `code` spans, [text](url) links, bare https:// URLs (GitHub renders
// those as clickable too, and plenty of release notes rely on that instead of bracket syntax),
// +/-/* bullet lists, #/##/### ATX headers, > quotes and GitHub's [!NOTE]-style alerts, so raw
// "**"/"`"/"[...](...)"/"+ "/"### "/"> " syntax doesn't leak into the UI.
internal val QUOTE_LINE = Regex("""^\s*>\s?(.*)$""")
internal val ALERT_LINE = Regex("""^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\s*$""", RegexOption.IGNORE_CASE)
private val HEADER_MARKS = Regex("""^#{1,6}\s+""")
internal val HEADER_LINE = Regex("^(#{1,6})\\s+(.*)$")

// a line that is nothing but bold text, which many release notes use as a section title instead of "###"
internal val BOLD_TITLE_LINE = Regex("^\\s*\\*\\*([^*]+)\\*\\*:?\\s*$")
internal val BULLET_LINE = Regex("^(\\s*)[+*-]\\s(.*)$")
private val HEADER_FONT_SIZE = 15.sp

// bundles the theme colors inline spans need into one, purely to keep appendWithInlineSpans
// under detekt's parameter-count threshold without losing each color's own name at the call site
private data class InlineSpanColors(
    val body: Color,
    val muted: Color,
    val link: Color,
    val codeText: Color,
    val codeBackground: Color,
)

@Composable
fun String.formatMarkdownLite(): AnnotatedString {
    val headerColor = MaterialTheme.colorScheme.onSurface
    val colors =
        InlineSpanColors(
            body = LocalContentColor.current,
            muted = MaterialTheme.colorScheme.onSurfaceVariant,
            link = MaterialTheme.colorScheme.primary,
            codeText = MaterialTheme.colorScheme.onSurfaceVariant,
            codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    val headerStyle = SpanStyle(fontWeight = FontWeight.Bold, fontSize = HEADER_FONT_SIZE, color = headerColor)
    return buildAnnotatedString {
        val writer = MarkdownLineWriter(this, headerStyle, colors)
        // "( https://... )" -> "(https://...)": release notes often space a trailing link out
        cleanReleaseNotes(this@formatMarkdownLite).replace(SPACED_PAREN_URL, "($1)").lines().forEach(writer::write)
    }
}

// writes release-note lines one paragraph each - no '\n' between them, which would double every gap
private class MarkdownLineWriter(
    private val builder: Builder,
    private val headerStyle: SpanStyle,
    private val colors: InlineSpanColors,
) {
    private var previousWasBlank = true

    // the depth of the bullet the previous line belonged to, so an indented follow-on line stays with it
    private var bulletDepth: Int? = null

    // the label of a GitHub alert ("Tip") waiting to lead its quote's first line
    private var alertLabel: String? = null

    fun write(line: String) {
        val quote = QUOTE_LINE.matchEntire(line)
        when {
            // blank runs (left behind by removed HTML comments, say) show as a single gap
            line.isBlank() && previousWasBlank -> Unit
            quote != null -> {
                writeQuote(quote.groupValues[1].trim())
                previousWasBlank = false
                bulletDepth = null
            }
            else -> {
                alertLabel = null
                writeLine(line)
            }
        }
    }

    private fun writeLine(line: String) {
        val header = HEADER_LINE.matchEntire(line) ?: BOLD_TITLE_LINE.matchEntire(line)
        val bullet = BULLET_LINE.matchEntire(line)
        val continuing = bulletDepth
        when {
            line.isBlank() -> builder.blankLine()
            header != null -> writeHeader(header.groupValues.last().trim())
            bullet != null -> writeBullet(depthOf(bullet.groupValues[1]), bullet.groupValues[2])
            continuing != null && line.first().isWhitespace() ->
                builder.withStyle(continuationParagraph(continuing)) { appendWithInlineSpans(line.trim(), colors) }
            else -> builder.withStyle(PLAIN_PARAGRAPH) { appendWithInlineSpans(line, colors) }
        }
        bulletDepth =
            when {
                bullet != null -> depthOf(bullet.groupValues[1])
                line.isBlank() || header != null || !line.first().isWhitespace() -> null
                else -> bulletDepth
            }
        previousWasBlank = line.isBlank()
    }

    // a heading gets a blank line above it, as a markdown renderer would space it
    private fun writeHeader(text: String) {
        if (!previousWasBlank) builder.blankLine()
        builder.withStyle(PLAIN_PARAGRAPH) { withStyle(headerStyle) { appendWithInlineSpans(text, colors) } }
    }

    private fun writeBullet(
        depth: Int,
        text: String,
    ) {
        builder.withStyle(bulletParagraph(depth)) {
            append(if (depth == 0) "•  " else "◦  ")
            appendWithInlineSpans(text, colors)
        }
    }

    // quoted lines read as a muted, indented note; a heading inside one is just its text
    private fun writeQuote(content: String) {
        val alert = ALERT_LINE.matchEntire(content)
        when {
            alert != null -> alertLabel = alert.groupValues[1].lowercase().replaceFirstChar { it.uppercase() }
            content.isNotEmpty() -> {
                val label = alertLabel
                builder.withStyle(QUOTE_PARAGRAPH) {
                    withStyle(SpanStyle(color = colors.muted)) {
                        if (label != null) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$label  ") }
                        appendWithInlineSpans(content.replace(HEADER_MARKS, ""), colors)
                    }
                }
                alertLabel = null
            }
        }
    }

    private fun depthOf(indent: String): Int = (indent.replace("\t", "  ").length / 2).coerceIn(0, MAX_BULLET_DEPTH)
}

private val PLAIN_PARAGRAPH = ParagraphStyle()
private val QUOTE_PARAGRAPH = ParagraphStyle(textIndent = TextIndent(firstLine = 12.sp, restLine = 12.sp))
private const val MAX_BULLET_DEPTH = 3
private const val BULLET_STEP_SP = 14f
private const val BULLET_HANG_SP = 13f

// a wrapped bullet line lines up under its text, not under the bullet
private fun bulletParagraph(depth: Int): ParagraphStyle =
    ParagraphStyle(
        textIndent =
            TextIndent(
                firstLine = (BULLET_STEP_SP * depth).sp,
                restLine = (BULLET_STEP_SP * depth + BULLET_HANG_SP).sp,
            ),
    )

// a bullet's follow-on line, lined up with the bullet's text
private fun continuationParagraph(depth: Int): ParagraphStyle {
    val indent = (BULLET_STEP_SP * depth + BULLET_HANG_SP).sp
    return ParagraphStyle(textIndent = TextIndent(firstLine = indent, restLine = indent))
}

// an empty line keeps its height as a paragraph holding a single space
private fun Builder.blankLine() {
    withStyle(PLAIN_PARAGRAPH) { append(" ") }
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
                    appendWithInlineSpans(remaining.substring(boldStart + 2, end), colors)
                }
                remaining = remaining.substring(end + 2)
            }
        }
    }
}

private val SPACED_PAREN_URL = Regex("""\(\s+(https?://\S+?)\s+\)""")

private val LINK_SYNTAX = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")

// excludes a trailing ')' from the match so "(see https://example.com/x)" doesn't swallow the
// closing paren into the link - the same heuristic GitHub's own bare-URL autolinker uses
private val BARE_URL = Regex("https?://[^\\s)]+")

private val GITHUB_COMMIT_URL = Regex("^https://github\\.com/[^/]+/[^/]+/commit/([0-9a-f]{7})[0-9a-f]*$")
private val GITHUB_NUMBERED_URL = Regex("^https://github\\.com/[^/]+/[^/]+/(?:pull|issues)/(\\d+)$")
private val GITHUB_COMPARE_URL = Regex("^https://github\\.com/[^/]+/[^/]+/compare/(.+?)\\.\\.\\.(.+)$")

// a full commit or pull request URL in release notes is noise for a reader - GitHub itself shows
// these as "a1b2c3d" and "#123", so do the same while keeping them tappable
private fun shortLinkText(url: String): String =
    GITHUB_COMMIT_URL.matchEntire(url)?.let { "commit ${it.groupValues[1]}" }
        ?: GITHUB_NUMBERED_URL.matchEntire(url)?.let { "#${it.groupValues[1]}" }
        ?: GITHUB_COMPARE_URL.matchEntire(url)?.let { "changes from ${it.groupValues[1]} to ${it.groupValues[2]}" }
        ?: url
