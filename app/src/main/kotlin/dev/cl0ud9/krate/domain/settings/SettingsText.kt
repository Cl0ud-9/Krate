package dev.cl0ud9.krate.domain.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

// an app's settings as the JSON text its Import / Export box holds. Some apps leave the outer braces off ("a": 1,
// "b": 2) and put them back on import, so text is read either way and written back the way the app writes it
internal object SettingsText {
    private val json = Json { prettyPrint = false }

    // the settings in the text; empty for a blank box (every setting at its default), null when it isn't JSON
    fun parse(text: String): JsonObject? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return JsonObject(emptyMap())
        val whole = if (trimmed.startsWith("{")) trimmed else "{$trimmed}"
        return runCatching { json.parseToJsonElement(whole).jsonObject }.getOrNull()
    }

    // the recommended settings laid over the current ones: everything set already stays, each recommended one is
    // set, and the current order is kept with new ones at the end
    fun merge(
        current: JsonObject,
        recommended: JsonObject,
    ): JsonObject = JsonObject(current + recommended)

    // the recommended settings that aren't already set that way
    fun changes(
        current: JsonObject,
        recommended: JsonObject,
    ): Set<String> = recommended.filter { (key, value) -> current[key] != value }.keys

    // one setting a line, the shape these boxes use, with braces only if the app writes them
    fun write(
        settings: JsonObject,
        braces: Boolean,
    ): String {
        val lines = settings.entries.joinToString(",\n") { (key, value) -> "${encode(key)}: ${encode(value)}" }
        return if (braces) "{\n$lines\n}" else lines
    }

    // whether the app writes braces: from what it exported, or for an empty box, the way the picks were written
    fun usesBraces(
        current: String,
        recommended: String,
    ): Boolean = (current.trim().ifEmpty { recommended.trim() }).startsWith("{")

    private fun encode(value: String) = encode(JsonPrimitive(value))

    private fun encode(value: JsonElement) = json.encodeToString(JsonElement.serializer(), value)
}
