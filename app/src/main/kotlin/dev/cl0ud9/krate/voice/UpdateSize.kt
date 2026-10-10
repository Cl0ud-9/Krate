package dev.cl0ud9.krate.voice

private const val PATCH_PARTS = 3

// how big a step an update is, read off the version numbers: a new first number is a major release, a change only in
// the third is a small fix. Anything else, or versions that aren't plain numbers, says nothing
fun updateSizeMoment(
    installed: String?,
    next: String?,
): Moment? {
    val from = versionParts(installed)
    val to = versionParts(next)
    return when {
        from == null || to == null -> null
        to[0] > from[0] -> Moment.UPDATE_MAJOR
        from.size >= PATCH_PARTS && to.size >= PATCH_PARTS && to[0] == from[0] && to[1] == from[1] && to[2] > from[2] ->
            Moment.UPDATE_SMALL
        else -> null
    }
}

// "v4.2.1-beta" reads as [4, 2, 1]; null when it doesn't start with a number
private fun versionParts(version: String?): List<Int>? {
    val numbers =
        version
            ?.trim()
            ?.removePrefix("v")
            ?.takeWhile { it.isDigit() || it == '.' }
            ?.split('.')
            ?.mapNotNull { it.toIntOrNull() }
            .orEmpty()
    return numbers.ifEmpty { null }
}
