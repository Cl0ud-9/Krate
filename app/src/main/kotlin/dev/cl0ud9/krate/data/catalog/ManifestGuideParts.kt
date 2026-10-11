package dev.cl0ud9.krate.data.catalog

import dev.cl0ud9.krate.domain.links.OpenLinks
import dev.cl0ud9.krate.domain.model.RecommendedSettings
import dev.cl0ud9.krate.domain.model.WalkStep
import dev.cl0ud9.krate.domain.settings.SettingsText
import java.security.MessageDigest

// left out unless the settings read as JSON with at least one setting in them, and say what they change
internal fun ManifestRecommendedDto.toDomain(): RecommendedSettings? =
    SettingsText.parse(settings)?.takeIf { it.isNotEmpty() && summary.isNotEmpty() }?.let { parsed ->
        // the settings themselves, not their spacing, decide the revision
        val canonical = SettingsText.write(parsed, braces = true)
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        val revision = digest.take(REVISION_BYTES).joinToString("") { "%02x".format(it) }
        RecommendedSettings(settings.trim(), summary, revision)
    }

private const val REVISION_BYTES = 8

// a step needs something to say; a link only opens if it's a web or Telegram one, never anything else
internal fun ManifestWalkStepDto.toDomain(): WalkStep? =
    takeIf { it.title.isNotBlank() && it.detail.isNotBlank() }?.let {
        WalkStep(
            title = title.trim(),
            detail = detail.trim(),
            copy = copy?.takeIf { text -> text.isNotBlank() },
            copyLabel = copyLabel?.takeIf { text -> text.isNotBlank() },
            link = link?.takeIf { url -> if (inApp) url.isAppOnlyLink() else url.isOpenableLink() },
            linkLabel = linkLabel?.takeIf { text -> text.isNotBlank() },
            inApp = inApp,
            confirm = confirm?.takeIf { text -> text.isNotBlank() && inApp },
            openApp = openApp,
        )
    }

private fun String.isOpenableLink(): Boolean = startsWith("https://") || startsWith("tg://")

// a link only its own app understands: a scheme of its own, never one that reaches the web, files or other apps
private fun String.isAppOnlyLink(): Boolean {
    val scheme = substringBefore("://", missingDelimiterValue = "").lowercase()
    return scheme.matches(Regex("[a-z][a-z0-9+.-]*")) && scheme !in SHARED_SCHEMES
}

private val SHARED_SCHEMES =
    setOf(
        "http",
        "https",
        "tg",
        "intent",
        "file",
        "content",
        "javascript",
        "data",
        "market",
        "android-app",
        "sms",
        "tel",
    )

// nothing to open is the same as no entry
internal fun ManifestOpenLinksDto.toDomain(): OpenLinks? =
    takeIf { hosts.isNotEmpty() || unlessInstalled.isNotEmpty() }?.let {
        OpenLinks(hosts.map { host -> host.trim() }.filter { host -> host.isNotEmpty() }, unlessInstalled)
    }
