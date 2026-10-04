package dev.cl0ud9.krate.ui.util

private val HTML_COMMENT = Regex("""<!--[\s\S]*?-->""")
private val HTML_TAG =
    Regex(
        """</?(details|summary|br|p|div|img|picture|source|sub|sup|kbd|b|i|em|strong|span|a|hr|h[1-6])\b[^>]*>""",
        RegexOption.IGNORE_CASE,
    )

// release notes as written for GitHub, minus what only makes sense there: HTML comments and tags, and alert blocks
// and tables telling readers which APK file to pick (Krate picks the right build itself). Other tables become
// bullets, rules become a gap, and a heading left with nothing under it goes too
internal fun cleanReleaseNotes(text: String): String {
    val withoutHtml =
        text
            .replace("<!-->", "")
            .replace(HTML_COMMENT, "")
            .replace(HTML_TAG, "")
    val lines = dropEmptySections(blocksOutsideKrate(withoutHtml.lines()).map { if (RULE_LINE.matches(it)) "" else it })
    return lines.joinToString("\n").trim()
}

private val RULE_LINE = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
private val TABLE_LINE = Regex("""^\s*\|.*\|\s*$""")
private val TABLE_DIVIDER = Regex("""^[\s|:-]+$""")

// quote blocks and tables, each taken whole: an APK-picking one is dropped, any other table becomes bullets
private fun blocksOutsideKrate(lines: List<String>): List<String> {
    val kept = mutableListOf<String>()
    var i = 0
    while (i < lines.size) {
        val pattern =
            when {
                QUOTE_LINE.matches(lines[i]) -> QUOTE_LINE
                TABLE_LINE.matches(lines[i]) -> TABLE_LINE
                else -> null
            }
        if (pattern == null) {
            kept += lines[i++]
            continue
        }
        var end = i
        while (end < lines.size && pattern.matches(lines[end])) end++
        val block = lines.subList(i, end)
        val aboutApks = block.any { it.contains(".apk", ignoreCase = true) }
        when {
            pattern == TABLE_LINE -> if (!aboutApks) kept += tableAsBullets(block)
            !(aboutApks && block.any { ALERT_LINE.matches(QUOTE_LINE.matchEntire(it)!!.groupValues[1].trim()) }) ->
                kept += block
        }
        i = end
    }
    return kept
}

// a table's rows as "first cell: the rest", its header row and divider left out
private fun tableAsBullets(rows: List<String>): List<String> =
    rows
        .filterNot { TABLE_DIVIDER.matches(it) }
        .drop(1)
        .map { row ->
            row
                .trim()
                .trim('|')
                .split('|')
                .map(String::trim)
                .filter(String::isNotEmpty)
        }.filter { it.isNotEmpty() }
        .map { cells -> "- " + cells.first() + if (cells.size > 1) ": " + cells.drop(1).joinToString(", ") else "" }

// a heading followed only by blank lines up to the next heading, or the end
private fun dropEmptySections(lines: List<String>): List<String> =
    lines.filterIndexed { index, line ->
        if (!HEADER_LINE.matches(line)) return@filterIndexed true
        val next = lines.drop(index + 1).firstOrNull { it.isNotBlank() }
        next != null && !HEADER_LINE.matches(next)
    }

// what a collapsed preview shows: the changes themselves, from the first heading or bullet, past a leading intro
// such as a donation note; notes without either preview from the top. The flag says whether an intro was skipped
internal fun releaseNotesPreview(text: String): Pair<String, Boolean> {
    val lines = cleanReleaseNotes(text).lines()
    val first =
        lines.indexOfFirst { HEADER_LINE.matches(it) || BOLD_TITLE_LINE.matches(it) || BULLET_LINE.matches(it) }
    return if (first > 0) lines.drop(first).joinToString("\n") to true else lines.joinToString("\n") to false
}
