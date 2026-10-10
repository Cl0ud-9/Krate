package dev.cl0ud9.krate.ui.settings

import android.content.Context

// only the license texts the app has to carry; the build copies them here (bundleLicenses in app/build.gradle.kts)
private const val LICENSE_ASSET_DIR = "licenses"
internal const val KRATE_LICENSE_FILE = "LICENSE"
internal const val APACHE_LICENSE_FILE = "apache-2.0.txt"
private const val APACHE_TERMS_END = "END OF TERMS AND CONDITIONS"

// where an entry's full text comes from: a bundled file, the Apache terms alone, or a short attribution
internal sealed interface LicenseText {
    data class File(
        val name: String,
    ) : LicenseText

    // the Apache terms alone, without the appendix on how to apply the license
    data object ApacheTerms : LicenseText

    data class Attribution(
        val text: String,
    ) : LicenseText
}

// one card in the Licenses sheet: what it is, its license, and what Krate uses it for
internal class LicenseEntry(
    val name: String,
    val license: String,
    val use: String,
    val text: LicenseText,
)

internal val LICENSES =
    listOf(
        LicenseEntry(
            name = "Krate",
            license = "Source available",
            use =
                "Krate's own code. Copyright 2026 Cloud/9. Free to read, use and contribute to, but not to " +
                    "redistribute.",
            text = LicenseText.File(KRATE_LICENSE_FILE),
        ),
        LicenseEntry(
            name = "Open-source libraries",
            license = "Apache License 2.0",
            use =
                "Android Jetpack and Compose, Kotlin, OkHttp and Okio, Google Tink, Guava, Gson, " +
                    "and Kyant's Backdrop for liquid glass.",
            text = LicenseText.ApacheTerms,
        ),
        LicenseEntry(
            name = "Protocol Buffers",
            license = "BSD 3-Clause",
            use = "Built into Google Tink, which checks signed catalogs and protects a saved GitHub token.",
            text = LicenseText.File("protobuf-BSD-3-Clause.txt"),
        ),
        LicenseEntry(
            name = "Google Sans Flex",
            license = "SIL Open Font License 1.1",
            use = "The typeface used throughout Krate.",
            text = LicenseText.File("google-sans-flex-OFL.txt"),
        ),
        LicenseEntry(
            name = "Font Awesome Free",
            license = "CC BY 4.0",
            use = "The Krate mark, the notification rocket and the GitHub mark.",
            text =
                LicenseText.Attribution(
                    "Icons by Font Awesome Free (fontawesome.com), Copyright Fonticons, Inc., licensed under " +
                        "Creative Commons Attribution 4.0 International: " +
                        "https://creativecommons.org/licenses/by/4.0/\n\n" +
                        "The shapes are unchanged, only scaled and converted to Android vector drawables. GitHub " +
                        "is a trademark of GitHub, Inc.",
                ),
        ),
        LicenseEntry(
            name = "Material Symbols",
            license = "Apache License 2.0",
            use = "Icons across the app, by Google.",
            text = LicenseText.ApacheTerms,
        ),
    )

// the bundled files the entries above read, which the build has to copy into the APK
internal val BUNDLED_LICENSE_FILES: Set<String> =
    LICENSES.mapNotNullTo(mutableSetOf()) { (it.text as? LicenseText.File)?.name } + APACHE_LICENSE_FILE

// an entry's full text, ready to show; reads the APK's bundled files, so call it off the main thread
internal fun readLicenseText(
    context: Context,
    entry: LicenseEntry,
): String {
    fun read(name: String): String =
        context.assets
            .open("$LICENSE_ASSET_DIR/$name")
            .bufferedReader()
            .use { it.readText() }
    val raw =
        when (val text = entry.text) {
            is LicenseText.File -> read(text.name)
            LicenseText.ApacheTerms -> apacheTermsOnly(read(APACHE_LICENSE_FILE))
            is LicenseText.Attribution -> text.text
        }
    return forDisplay(raw)
}

internal fun apacheTermsOnly(license: String): String =
    license.substringBefore(APACHE_TERMS_END).trim() + "\n\n" + APACHE_TERMS_END

// reflowed, then without the indentation plain-text files use for layout, which reads oddly in a proportional font
internal fun forDisplay(text: String): String =
    reflowLicenseText(text.trim()).lines().joinToString("\n") { it.trimStart() }

// a line this long in a license file was wrapped by its author, not ended
private const val WRAPPED_LINE_MIN = 55
private val LIST_MARKER = Regex("""^(\(?[0-9a-zA-Z]{1,3}[.)]|[-*•])\s""")

// license files are hard-wrapped at about 80 columns, which re-wraps into ragged half-lines on a phone; this joins a
// wrapped sentence back into one line but leaves blank lines, list items, indentation changes and aligned tables alone
internal fun reflowLicenseText(text: String): String {
    val out = mutableListOf<String>()
    for (line in text.replace("\r\n", "\n").lines()) {
        val previous = out.lastOrNull()
        if (previous != null && continuesWrappedLine(previous, line)) {
            out[out.lastIndex] = previous.trimEnd() + " " + line.trim()
        } else {
            out += line.trimEnd()
        }
    }
    return out.joinToString("\n")
}

private fun continuesWrappedLine(
    previous: String,
    line: String,
): Boolean {
    val body = line.trim()
    // a list item's own continuation lines hang under its text, past the marker
    val previousIndent = previous.takeWhile { it == ' ' }.length
    val marker = LIST_MARKER.find(previous.trim())?.value?.length ?: 0
    return previous.isNotBlank() &&
        body.isNotEmpty() &&
        line.takeWhile { it == ' ' }.length == previousIndent + marker &&
        previous.trimEnd().length >= WRAPPED_LINE_MIN &&
        !LIST_MARKER.containsMatchIn(body) &&
        !isRule(previous) &&
        !isRule(body) &&
        "  " !in previous.trim() &&
        "  " !in body
}

// a divider drawn with dashes or the like, never part of a sentence
private fun isRule(line: String): Boolean = line.isNotBlank() && line.trim().all { it in "-=_*~" }
