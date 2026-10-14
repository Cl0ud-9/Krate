package dev.cl0ud9.krate.platform.diagnostics

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val FILE_NAME = "krate-log.txt"

// the log keeps about this much; older lines go first, so it never grows into something worth worrying about
private const val MAX_BYTES = 48 * 1024
private const val CRASH_LINES = 25
private const val LINE_LIMIT = 400

// a short running record of what Krate did that could matter when something goes wrong: what the helper was asked to
// do and how it ended, what came in front of it, and any crash. It's kept on the phone only, holds no settings values,
// tokens or web addresses, and leaves the phone only inside a report the person chooses to share. Android lets an app
// read only its own system log lines and those are gone after a restart, so Krate keeps this one itself
object KrateLog {
    private val stamp = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss", Locale.US)

    @Volatile
    private var file: File? = null

    fun init(context: Context) {
        file = File(context.applicationContext.filesDir, FILE_NAME)
    }

    @Synchronized
    fun event(
        what: String,
        detail: String = "",
    ) {
        val target = file ?: return
        val time = stamp.format(Instant.now().atZone(ZoneId.systemDefault()))
        val line = "$time $what ${detail.replace('\n', ' ')}".trimEnd().take(LINE_LIMIT)
        runCatching {
            target.appendText(line + "\n")
            if (target.length() > MAX_BYTES) trim(target)
        }
    }

    // the whole log, oldest first; empty when there's nothing yet
    @Synchronized
    fun read(maxLines: Int = DEFAULT_REPORT_LINES): String =
        runCatching {
            file
                ?.takeIf { it.exists() }
                ?.readLines()
                ?.takeLast(maxLines)
                ?.joinToString("\n")
        }.getOrNull()
            .orEmpty()

    @Synchronized
    fun clear() {
        runCatching { file?.delete() }
    }

    // a crash, in the log before the process goes; only the top of its trace, which is where the cause is
    fun crash(
        thread: String,
        error: Throwable,
    ) {
        val trace = error.stackTraceToString().lines().take(CRASH_LINES)
        event("CRASH", "on $thread: ${trace.firstOrNull().orEmpty()}")
        trace.drop(1).forEach { event("  ", it.trim()) }
    }

    private fun trim(target: File) {
        val kept = target.readLines().takeLast(MAX_BYTES / AVERAGE_LINE / 2)
        target.writeText(kept.joinToString("\n") + "\n")
    }

    const val DEFAULT_REPORT_LINES = 80
    private const val AVERAGE_LINE = 80
}
