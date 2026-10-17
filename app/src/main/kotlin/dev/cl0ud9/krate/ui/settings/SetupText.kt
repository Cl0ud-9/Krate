package dev.cl0ud9.krate.ui.settings

import dev.cl0ud9.krate.platform.setup.SetupApplied

private const val NAMES_SHOWN = 4

// what the file would do, said before it does it: nothing is added without this being read first
internal fun loadSummary(
    names: List<String>,
    manual: Int,
    alreadyHere: Int,
    unusable: Int,
): String {
    val lines = mutableListOf<String>()
    if (names.isNotEmpty()) lines += "• start tracking ${namesText(names)}"
    if (manual > 0) lines += "• keep $manual ${if (manual == 1) "app" else "apps"} on manual updates"
    lines += "• set Krate's look and download choices as they were"
    val notes = mutableListOf<String>()
    if (alreadyHere == 1) notes += "1 is already here and stays as it is."
    if (alreadyHere > 1) notes += "$alreadyHere are already here and stay as they are."
    if (unusable > 0) notes += "$unusable ${if (unusable == 1) "entry" else "entries"} in the file couldn't be used."
    val tail = if (notes.isEmpty()) "" else "\n\n" + notes.joinToString(" ")
    return "Krate will:\n" + lines.joinToString("\n") + tail
}

// "A", "A and B", "A, B and C", or "A, B, C and 2 more"
internal fun namesText(names: List<String>): String =
    when {
        names.size <= 1 -> names.joinToString()
        names.size <= NAMES_SHOWN -> names.dropLast(1).joinToString(", ") + " and " + names.last()
        else -> names.take(NAMES_SHOWN - 1).joinToString(", ") + " and ${names.size - (NAMES_SHOWN - 1)} more"
    }

internal fun appliedText(
    done: SetupApplied,
    skippedInFile: Int,
): String {
    val parts = mutableListOf<String>()
    if (done.trackedAdded > 0) {
        parts += "now tracking ${done.trackedAdded} ${if (done.trackedAdded == 1) "app" else "apps"}"
    }
    if (done.trackedSkipped > 0) parts += "${done.trackedSkipped} already here"
    if (skippedInFile > 0) parts += "$skippedInFile couldn't be used"
    parts += "${done.settingsSet} choices set"
    return "Loaded: " + parts.joinToString(", ") + "."
}
