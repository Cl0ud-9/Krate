package dev.cl0ud9.krate.domain.model

// what an app needs once it's installed, the settings worth knowing, and how to keep them across a reinstall; written
// per app in the catalog and shown in App Details' setup card
data class AppGuide(
    val setup: List<SetupStep> = emptyList(),
    // where the app keeps its settings, in its own words ("Settings > Data and storage")
    val settingsWhere: String? = null,
    val tips: List<String> = emptyList(),
    // how to save the app's settings and data, and how to bring them back after a reinstall from scratch
    val backup: String? = null,
    val restore: String? = null,
    // for apps whose backup is text in a box of their own, the way there, so Krate can save and restore it itself
    val autoBackup: AutoBackup? = null,
    // settings the catalog recommends for the app, applied on request through the same box as automatic backups
    val recommended: RecommendedSettings? = null,
    // for setups that happen inside the app, one short screen at a time
    val walkthrough: List<WalkStep> = emptyList(),
) {
    val isEmpty: Boolean
        get() = setup.isEmpty() && tips.isEmpty() && backup == null && recommended == null && walkthrough.isEmpty()
}

// one screen of a walkthrough: what to do, in a line or two, with the text to copy and the link to open when there are
// any, so someone never has to type or hunt for either
data class WalkStep(
    val title: String,
    val detail: String,
    val copy: String? = null,
    val copyLabel: String? = null,
    val link: String? = null,
    val linkLabel: String? = null,
    // the link is one only the app itself understands, so it's opened inside that app and nowhere else
    val inApp: Boolean = false,
    // the button the app then asks to be tapped (its own "Add"), which Krate's helper can tap for the person
    val confirm: String? = null,
    // a button that opens the app, for a step that starts there
    val openApp: Boolean = false,
)

// the taps from an app's first screen to the box holding its settings as text, and the buttons that close that box
// and apply text pasted into it
data class AutoBackup(
    val path: List<UiTarget>,
    val close: String,
    val apply: String,
)

// the catalog's picks for an app: the settings as the app's own export writes them, what they change in plain words,
// and a revision that changes whenever the picks do, so Krate can tell an applied set from a newer one
data class RecommendedSettings(
    val text: String,
    val summary: List<String>,
    val revision: String,
)

// something on screen to tap, by its visible text or, for an icon, the label read out for it
data class UiTarget(
    val text: String? = null,
    val description: String? = null,
    // only on some versions of the app's screens: tapped when it's there, skipped when it isn't
    val optional: Boolean = false,
)

data class SetupStep(
    val kind: SetupKind,
    val title: String,
    // why the app needs it, in a sentence
    val detail: String,
    val optional: Boolean = false,
    // the accessibility or notification-listener service to turn on, as package/class, so Krate can tell when it's on
    val service: String? = null,
)

// the permissions and switches Krate can open straight to; IN_APP is a step done inside the app itself
enum class SetupKind {
    ACCESSIBILITY,
    USAGE_ACCESS,
    ALL_FILES_ACCESS,
    INSTALL_APPS,
    NOTIFICATIONS,

    // reading other apps' notifications (a scrobbler seeing what's playing), switched on per service
    NOTIFICATION_ACCESS,
    BATTERY,
    IN_APP,
}
