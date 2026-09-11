package dev.cl0ud9.manager.ui.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

private typealias Builder = AnnotatedString.Builder

// GitHub release bodies are markdown (release notes shown on AppDetailsScreen come straight from
// GitHub releases, section 9/42.9 of the spec), but the app has no full markdown renderer - a real
// engine is overkill for what actually shows up in practice. This cleans up the constructs that do
// show up: **bold** spans, +/-/* bullet lists, and #/##/### ATX headers, so raw "**"/"+ "/"### " syntax
// doesn't leak into the UI.
private val HEADER_LINE = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET_LINE = Regex("^(\\s*)[+*-]\\s(.*)$")
private val HEADER_FONT_SIZE = 15.sp

fun String.formatMarkdownLite(): AnnotatedString =
    buildAnnotatedString {
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
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = HEADER_FONT_SIZE)) {
                        appendWithBoldSpans(text.trim())
                    }
                }

                bullet != null -> {
                    val (indent, rest) = bullet.destructured
                    append(indent)
                    append(if (indent.isEmpty()) "• " else "◦ ")
                    appendWithBoldSpans(rest)
                }

                else -> appendWithBoldSpans(line)
            }
            previousWasBlank = line.isBlank()
        }
    }

private fun Builder.appendWithBoldSpans(text: String) {
    var remaining = text
    while (true) {
        val start = remaining.indexOf("**")
        val end = if (start == -1) -1 else remaining.indexOf("**", start + 2)
        if (start == -1 || end == -1) {
            append(remaining)
            return
        }
        append(remaining.substring(0, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(remaining.substring(start + 2, end))
        }
        remaining = remaining.substring(end + 2)
    }
}
