package dev.cl0ud9.manager.ui.components

data class ChangelogEntry(
    val title: String,
    val notes: List<String>,
)

// the manager's own real feature history, grouped by what actually shipped, not a fabricated
// per-build log - there's only one real version so far, so this reads as "what this build can do"
// grouped by when each part landed, rather than invented version numbers
val ManagerChangelog =
    listOf(
        ChangelogEntry(
            title = "Redesigned shell and onboarding",
            notes =
                listOf(
                    "Floating pill navigation bar and a lighter, transparent top bar",
                    "A morphing, rotating action button and animated steps in first-run setup",
                    "Smoother back navigation with Material's Emphasized-easing motion",
                ),
        ),
        ChangelogEntry(
            title = "Home and Settings, redesigned",
            notes =
                listOf(
                    "A status hero card instead of bare numbers - shows what needs attention",
                    "Recent activity is now real: every install and update is logged locally",
                    "Settings reorganized as icon-badged rows instead of plain text blocks",
                    "A proactive update announcement when a newer manager build is available",
                ),
        ),
        ChangelogEntry(
            title = "Pull-to-refresh and a shared catalog cache",
            notes =
                listOf(
                    "Pull-to-refresh on Home, Apps, Updates and App Details",
                    "One shared network fetch instead of every screen re-fetching independently",
                ),
        ),
        ChangelogEntry(
            title = "Manager self-update checker",
            notes = listOf("Checks this repository's GitHub Releases for a newer manager build"),
        ),
        ChangelogEntry(
            title = "Installing and updating apps",
            notes =
                listOf(
                    "Download, verify, and install through Android's PackageInstaller",
                    "Dependency-ordered Update All with a running result summary",
                    "Clean-install fallback and rollback when an in-place update fails",
                    "A background check for pending updates independent of push notifications",
                ),
        ),
    )
