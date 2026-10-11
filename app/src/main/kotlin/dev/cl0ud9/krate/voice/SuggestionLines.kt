package dev.cl0ud9.krate.voice

import dev.cl0ud9.krate.domain.suggest.SuggestionKind
import kotlin.math.abs

private val WORTH_A_TRY =
    listOf(
        "Worth a try",
        "Might be your next favourite",
        "Pulled off the shelf for you",
        "Have you met this one?",
        "A good fit for you",
        "Something you might love",
    )

private val NEW_ARRIVAL =
    listOf(
        "New in the Krate",
        "Just unpacked",
        "Fresh on the shelf",
        "Hot off the shelf",
    )

private val BECAUSE =
    listOf(
        "You've got %s, so this one's a natural fit.",
        "Since you have %s, you'll feel right at home.",
        "Pairs nicely with your %s.",
    )

// the wording of a suggestion: a warm headline and, when an app of theirs made it a fit, the reason. Steady for an app
// within a day, so the card doesn't reword itself each time it's drawn; plain when playful messages are off
internal object SuggestionLines {
    fun kicker(
        kind: SuggestionKind,
        appId: String,
        nowMillis: Long,
    ): String {
        val plain = if (kind == SuggestionKind.NEW) "New in the Krate" else "Suggested for you"
        if (!KrateVoice.playful) return plain
        val pool = if (kind == SuggestionKind.NEW) NEW_ARRIVAL else WORTH_A_TRY
        return pool[indexFor(appId, nowMillis, pool.size)]
    }

    fun because(
        installedLabel: String,
        appId: String,
        nowMillis: Long,
    ): String {
        if (!KrateVoice.playful) return "Because you have $installedLabel."
        return BECAUSE[indexFor(appId, nowMillis, BECAUSE.size)].format(installedLabel)
    }

    private fun indexFor(
        appId: String,
        nowMillis: Long,
        size: Int,
    ): Int = abs("$appId${nowMillis / DAY_MS}".hashCode()) % size
}
