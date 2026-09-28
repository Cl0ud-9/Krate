package dev.cl0ud9.krate.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// the Licenses sheet reads license texts the build copies from the repo into the APK; they have to line up
class LicenseCatalogTest {
    // unit tests run with the app module as the working directory
    private val repoRoot = File("..")

    private fun repoFile(name: String): File =
        if (name == APACHE_LICENSE_FILE) File(repoRoot, name) else File(repoRoot, "licenses/$name")

    @Test
    fun everyBundledFileExistsInTheRepo() {
        BUNDLED_LICENSE_FILES.forEach { name -> assertTrue("missing $name", repoFile(name).isFile) }
    }

    @Test
    fun theBuildBundlesExactlyWhatTheSheetReads() {
        val buildScript = File("build.gradle.kts").readText()
        val bundled =
            Regex("""rootProject\.file\("(?:licenses/)?([^"]+)"\)""")
                .findAll(
                    buildScript
                        .substringAfter(
                            "tasks.register<BundleLicensesTask>",
                        ).substringBefore("androidComponents"),
                ).map { it.groupValues[1] }
                .toSet()
        assertEquals(BUNDLED_LICENSE_FILES, bundled)
    }

    @Test
    fun namesAreUniqueAndNothingIsBlank() {
        assertEquals(LICENSES.size, LICENSES.map { it.name }.toSet().size)
        LICENSES.forEach { assertTrue(it.name, it.license.isNotBlank() && it.use.isNotBlank()) }
    }

    @Test
    fun apacheTermsLeaveOutKratesAppendix() {
        val terms = apacheTermsOnly(File(repoRoot, APACHE_LICENSE_FILE).readText())
        assertTrue(terms.endsWith("END OF TERMS AND CONDITIONS"))
        assertTrue("Cloud/9" !in terms)
    }

    @Test
    fun displayTextHasNoLayoutIndentation() {
        val shown = forDisplay(File(repoRoot, APACHE_LICENSE_FILE).readText())
        assertTrue(shown.lines().none { it.startsWith(" ") })
    }

    @Test
    fun reflowJoinsWrappedSentences() {
        val text =
            "Permission is hereby granted, free of charge, to any person obtaining a copy\n" +
                "of this software and associated documentation files (the \"Software\"), to deal\n" +
                "in the Software."
        assertEquals(
            "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and " +
                "associated documentation files (the \"Software\"), to deal in the Software.",
            reflowLicenseText(text),
        )
    }

    @Test
    fun reflowKeepsParagraphsListsAndTables() {
        val text =
            "      (a) You must give any other recipients of the Work or Derivative Works a copy of\n" +
                "          this License; and\n" +
                "\n" +
                "      (b) You must cause any modified files to carry prominent notices stating that You\n" +
                "\n" +
                "  ic_nav_home_outline.xml     <- home/materialsymbolsrounded/home_24px.xml\n" +
                "  ic_nav_home_filled.xml      <- home/materialsymbolsrounded/home_fill1_24px.xml\n" +
                "Short title\n" +
                "Next line"
        assertEquals(
            listOf(
                "      (a) You must give any other recipients of the Work or Derivative Works a copy of " +
                    "this License; and",
                "",
                "      (b) You must cause any modified files to carry prominent notices stating that You",
                "",
                "  ic_nav_home_outline.xml     <- home/materialsymbolsrounded/home_24px.xml",
                "  ic_nav_home_filled.xml      <- home/materialsymbolsrounded/home_fill1_24px.xml",
                "Short title",
                "Next line",
            ),
            reflowLicenseText(text).lines(),
        )
    }

    @Test
    fun reflowKeepsDividerLinesOnTheirOwn() {
        val text =
            "-----------------------------------------------------------\n" +
                "SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007\n" +
                "-----------------------------------------------------------"
        assertEquals(text.lines(), reflowLicenseText(text).lines())
    }

    @Test
    fun reflowReadsEveryBundledFileWithoutLosingWords() {
        File(repoRoot, "licenses").listFiles().orEmpty().toList().plus(File(repoRoot, "LICENSE")).forEach { file ->
            val original = file.readText()
            val words = { s: String -> s.split(Regex("\\s+")).filter { it.isNotEmpty() } }
            assertEquals(file.name, words(original), words(reflowLicenseText(original)))
        }
    }

    @Test
    fun apacheTermsAreFoundInLicense() {
        assertTrue(File(repoRoot, "LICENSE").readText().contains("END OF TERMS AND CONDITIONS"))
    }
}
