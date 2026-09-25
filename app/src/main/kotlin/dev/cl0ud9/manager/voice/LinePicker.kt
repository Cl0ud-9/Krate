package dev.cl0ud9.manager.voice

import kotlin.random.Random

private const val RECENT_MEMORY = 2

// picks a line from a pool, skipping the ones this pool used most recently so the same words never show twice in a row
class LinePicker(
    private val random: Random = Random.Default,
) {
    private val recentByKey = mutableMapOf<String, ArrayDeque<String>>()

    @Synchronized
    fun pick(
        key: String,
        lines: List<String>,
        avoid: String? = null,
    ): String {
        require(lines.isNotEmpty()) { "pool $key is empty" }
        val recent = recentByKey.getOrPut(key) { ArrayDeque() }
        val skipped = recent.toSet() + listOfNotNull(avoid)
        // fall back to anything but the avoided line, then to anything at all, for pools too small to skip
        val candidates = lines.filter { it !in skipped }.ifEmpty { lines.filter { it != avoid } }.ifEmpty { lines }
        val line = candidates.random(random)
        recent.addLast(line)
        while (recent.size > minOf(RECENT_MEMORY, lines.size - 1)) recent.removeFirst()
        return line
    }
}
