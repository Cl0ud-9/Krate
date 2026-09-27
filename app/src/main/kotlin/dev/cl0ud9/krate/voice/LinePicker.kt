package dev.cl0ud9.krate.voice

import kotlin.random.Random

private const val RECENT_COOLDOWN = 2

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
// out the start of the next, so the same words never show twice within three picks
class LinePicker(
    private val random: Random = Random.Default,
    var memory: LineMemory = InMemoryLineMemory(),
) {
    @Synchronized
    fun pick(
        key: String,
        lines: List<String>,
    ): String {
        require(lines.isNotEmpty()) { "pool $key is empty" }
        val used = memory.used(key).filter { it in lines }
        // a new round starts with the previous round's last lines already counted, so they cool down first
        val round = if (lines.any { it !in used }) used else used.takeLast(minOf(RECENT_COOLDOWN, lines.size - 1))
        val line = lines.filter { it !in round }.random(random)
        memory.save(key, round + line)
        return line
    }
}
