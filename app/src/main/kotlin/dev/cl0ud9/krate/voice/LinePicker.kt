package dev.cl0ud9.krate.voice

import kotlin.random.Random

private const val RECENT_COOLDOWN = 2

// how many lines, from any pool, a new line tries not to echo, and the words too common to count as an echo
private const val ECHO_WINDOW = 4
private const val MIN_WORD = 4
private val COMMON_WORDS =
    setOf(
        "krate",
        "krate's",
        "your",
        "that",
        "this",
        "with",
        "what",
        "just",
        "here",
        "there",
        "from",
        "have",
        "into",
        "some",
        "more",
        "been",
        "it's",
        "that's",
        "what's",
        "here's",
        "there's",
        "you're",
        "will",
        "still",
        "keeper",
    )

// the words that make a line recognisable, so two lines in a row don't lean on the same one
internal fun keyWords(line: String): Set<String> =
    line
        .lowercase()
        .split(Regex("[^a-z']+"))
        .map { it.trim('\'') }
        .filter { it.length >= MIN_WORD && it !in COMMON_WORDS }
        .toSet()

// where a pool's used lines are kept; the default forgets them with the process, KrateVoice keeps them across launches
interface LineMemory {
    fun used(key: String): List<String>

    fun save(
        key: String,
        used: List<String>,
    )
}

class InMemoryLineMemory : LineMemory {
    private val byKey = mutableMapOf<String, List<String>>()

    override fun used(key: String): List<String> = byKey[key].orEmpty()

    override fun save(
        key: String,
        used: List<String>,
    ) {
        byKey[key] = used
    }
}

// a shuffle bag per pool: every line comes up once before any comes up again, and the last two of a round sit
// out the start of the next, so the same words never show twice within three picks. Across pools, a line that
// shares no key word with the last few shown is preferred, so "fresh" and "shelf" don't pile up on one screen
class LinePicker(
    private val random: Random = Random.Default,
    var memory: LineMemory = InMemoryLineMemory(),
) {
    // the last few lines picked from any pool, for this run of the app
    private var recent = emptyList<String>()

    @Synchronized
    fun pick(
        key: String,
        lines: List<String>,
    ): String {
        require(lines.isNotEmpty()) { "pool $key is empty" }
        val used = memory.used(key).filter { it in lines }
        // a new round starts with the previous round's last lines already counted, so they cool down first
        val round = if (lines.any { it !in used }) used else used.takeLast(minOf(RECENT_COOLDOWN, lines.size - 1))
        val candidates = lines.filter { it !in round }
        val echoed = recent.flatMap(::keyWords).toSet()
        val line = candidates.filter { keyWords(it).none(echoed::contains) }.ifEmpty { candidates }.random(random)
        memory.save(key, round + line)
        recent = (recent + line).takeLast(ECHO_WINDOW)
        return line
    }
}
