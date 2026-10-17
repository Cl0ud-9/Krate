package dev.cl0ud9.krate.domain.setup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// everything about how Krate is set up that is worth carrying to a new phone or back after a clean install: the apps
// someone tracks themselves, which apps they keep on manual updates, and the look and download choices. Never a
// password, a token, an app's own settings or anything that identifies the phone
@Serializable
data class KrateSetup(
    val version: Int = SETUP_VERSION,
    val exportedAtMillis: Long = 0L,
    val tracked: List<SetupTracked> = emptyList(),
    val manualUpdates: List<String> = emptyList(),
    val settings: SetupSettings = SetupSettings(),
)

// an app followed from a repository; what it needs to be followed again and held to the same developer
@Serializable
data class SetupTracked(
    val forge: String,
    val owner: String,
    val repo: String,
    val packageName: String,
    val displayName: String,
    val certificateSha256: String,
    val fileShape: String,
    val includePrerelease: Boolean = false,
)

@Serializable
data class SetupSettings(
    val themeMode: String? = null,
    val navBarStyle: String? = null,
    val navBarCornerRadius: Int? = null,
    val navBarCompactMode: Boolean? = null,
    val liquidGlass: Boolean? = null,
    val defaultLaunchTab: String? = null,
    val automaticDownloads: Boolean? = null,
    val downloadOnMobileData: Boolean? = null,
    val autoInstallUpdates: Boolean? = null,
)

const val SETUP_VERSION = 1

// the most a file may hold and still be read: a real setup is a few kilobytes, so anything far past that is not one
const val MAX_SETUP_BYTES = 512 * 1024
private const val MAX_TRACKED = 200
private const val MAX_MANUAL = 500
private const val MAX_TEXT = 200
private const val SHA256_HEX = 64
private const val MAX_RADIUS = 64

private val NAME_PART = Regex("^[A-Za-z0-9._-]+$")
private val OWNER_PART = Regex("^[A-Za-z0-9._/-]+$")
private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
private val HEX = Regex("^[0-9a-fA-F]+$")
private val FORGES = setOf("github", "codeberg", "gitlab")
private val THEMES = setOf("LIGHT", "DARK", "SYSTEM")
private val NAV_STYLES = setOf("FLOATING_PILL", "FULL_WIDTH")
private val TABS = setOf("HOME", "APPS", "UPDATES")
private val reader = Json { ignoreUnknownKeys = true }
private val writer =
    Json {
        prettyPrint = true
        encodeDefaults = true
    }

// what reading a file came to
sealed interface SetupReading {
    data class Ready(
        val setup: KrateSetup,
        // entries left out as unusable, so the person is told rather than left guessing
        val skipped: Int,
    ) : SetupReading

    data object NotASetup : SetupReading

    data class FromANewerKrate(
        val version: Int,
    ) : SetupReading
}

fun encodeSetup(setup: KrateSetup): String = writer.encodeToString(KrateSetup.serializer(), setup)

// the file's text as a setup, with every entry checked: ones that don't look right are dropped (and counted), nothing
// is trusted for being in the file. A setup from a newer Krate is refused, not guessed at
fun readSetup(text: String): SetupReading {
    if (text.length > MAX_SETUP_BYTES) return SetupReading.NotASetup
    val parsed = runCatching { reader.decodeFromString(KrateSetup.serializer(), text) }.getOrNull()
    return when {
        parsed == null || parsed.version < 1 -> SetupReading.NotASetup
        parsed.version > SETUP_VERSION -> SetupReading.FromANewerKrate(parsed.version)
        else -> checked(parsed)
    }
}

private fun checked(parsed: KrateSetup): SetupReading.Ready {
    val tracked =
        parsed.tracked
            .take(MAX_TRACKED)
            .filter { it.looksRight() }
            .distinctBy { it.key() }
    val manual =
        parsed.manualUpdates
            .take(MAX_MANUAL)
            .filter { it.length in 1..MAX_TEXT }
            .distinct()
    val kept = parsed.copy(tracked = tracked, manualUpdates = manual, settings = parsed.settings.checked())
    return SetupReading.Ready(kept, skipped = (parsed.tracked.size - tracked.size).coerceAtLeast(0))
}

fun SetupTracked.key(): String = "$forge/$owner/$repo".lowercase()

private fun SetupTracked.looksRight(): Boolean =
    forge in FORGES &&
        owner.length <= MAX_TEXT &&
        repo.length <= MAX_TEXT &&
        OWNER_PART.matches(owner) &&
        NAME_PART.matches(repo) &&
        PACKAGE.matches(packageName) &&
        displayName.isNotBlank() &&
        displayName.length <= MAX_TEXT &&
        certificateSha256.length == SHA256_HEX &&
        HEX.matches(certificateSha256) &&
        fileShape.isNotBlank() &&
        fileShape.length <= MAX_TEXT

// the settings a file may carry, each kept only if it's a value Krate has; anything else is left unset, so the phone
// keeps what it had
private fun SetupSettings.checked(): SetupSettings =
    copy(
        themeMode = themeMode?.takeIf { it in THEMES },
        navBarStyle = navBarStyle?.takeIf { it in NAV_STYLES },
        navBarCornerRadius = navBarCornerRadius?.takeIf { it in 0..MAX_RADIUS },
        defaultLaunchTab = defaultLaunchTab?.takeIf { it in TABS },
    )
