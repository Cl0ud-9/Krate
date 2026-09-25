package dev.cl0ud9.manager.voice

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import java.time.LocalTime
import kotlin.random.Random

// Krate's voice: every playful line lives here, one pool per moment. A line is only ever the
// headline - the plain facts (which app, what happened, what to do) always sit next to it
enum class Moment(
    val lines: List<String>,
) {
    GREETING(
        listOf(
            "Welcome back, Krate keeper.",
            "Welcome back, explorer.",
            "You're back.",
            "Hey there.",
            "Hello again.",
            "Look who's here.",
            "Krate is awake.",
            "Krate is ready.",
            "Back for more?",
            "Ready when you are.",
            "Good to have you here.",
            "The Krate is open.",
            "The lid is off.",
            "Let's see what's new.",
            "Let's unpack.",
            "Your apps are waiting.",
            "What's going into the Krate today?",
            "What's the loot today?",
            "Ready to unpack some updates?",
            "Let's get this Krate moving.",
            "Krate o'clock.",
            "You rang?",
            "Open the Krate. See what's inside.",
            "Let's make some app magic.",
            "All systems packed.",
            "Everything in its place.",
            "Right where you left it.",
            "Another day, another update.",
        ),
    ),
    GREETING_MORNING(
        listOf(
            "Good morning, Krate keeper.",
            "Rise and Krate.",
            "Morning! Coffee first?",
            "Fresh morning, fresh apps.",
            "Early bird gets the updates.",
        ),
    ),
    GREETING_AFTERNOON(
        listOf(
            "Good afternoon.",
            "Afternoon check-in?",
            "Unpacking on your lunch break?",
            "Halfway through the day already.",
        ),
    ),
    GREETING_EVENING(
        listOf(
            "Good evening.",
            "Evening, Krate keeper.",
            "Winding down?",
            "One last look before tonight?",
        ),
    ),
    GREETING_NIGHT(
        listOf(
            "Hello, night owl.",
            "Burning the midnight oil?",
            "Up late, huh?",
            "The Krate never sleeps.",
        ),
    ),
    ALL_CAUGHT_UP(
        listOf(
            "You're all caught up.",
            "The shelves are looking good.",
            "Nothing new in the Krate.",
            "Nothing to unpack today.",
            "The Krate is looking good.",
            "All packed.",
            "Everything's packed.",
            "Neat and tidy.",
        ),
    ),
    UPDATES_WAITING(
        listOf(
            "Psst... something's waiting.",
            "Fresh stuff in the Krate.",
            "Something new landed.",
            "New arrivals.",
            "Ready to unpack?",
            "The Krate has updates.",
            "A new one is waiting.",
        ),
    ),
    KRATE_UPDATE_AVAILABLE(
        listOf(
            "A fresh Krate is ready.",
            "Krate got an upgrade.",
            "Time for a fresh Krate.",
            "Something new for the Krate itself.",
        ),
    ),
    KRATE_UPDATED(
        listOf(
            "Freshly unpacked.",
            "Upgrade complete.",
            "Krate is back, and better.",
            "New Krate, same you.",
        ),
    ),
    KRATE_LATEST(
        listOf(
            "Freshest Krate there is.",
            "Nothing newer yet.",
            "Still the newest Krate.",
        ),
    ),
    CHECKING_FOR_UPDATES(
        listOf(
            "Checking for updates...",
            "Peeking for a newer Krate...",
            "Checking the shelves for updates...",
            "Looking for a fresh Krate...",
        ),
    ),
    INSTALLED(
        listOf(
            "Into the Krate it goes.",
            "And... it's in.",
            "All tucked in.",
            "Successfully packed.",
            "Ready to roll.",
        ),
    ),
    PARTLY_INSTALLED(
        listOf(
            "Mostly packed.",
            "Nearly everything made it in.",
            "Almost there.",
        ),
    ),
    INSTALL_FAILED(
        listOf(
            "That one escaped the Krate.",
            "That didn't quite fit.",
            "Oops. That didn't make it in.",
            "Well... that didn't go to plan.",
        ),
    ),
    DOWNLOADED(
        listOf(
            "Packed and ready.",
            "That's safely in the Krate.",
            "Download complete. Nice.",
            "One more thing packed.",
            "Freshly downloaded.",
        ),
    ),
    DOWNLOAD_FAILED(
        listOf(
            "That download slipped away.",
            "The download hit a snag.",
            "That one got lost on the way.",
            "Well... that didn't go to plan.",
        ),
    ),
    REFRESH_FAILED(
        listOf(
            "The Krate couldn't check in.",
            "We lost the trail.",
            "That refresh didn't make it.",
            "The Krate needs a little internet.",
        ),
    ),
    NOTHING_UNPACKED(
        listOf(
            "Nothing unpacked yet.",
            "A fresh, empty Krate.",
        ),
    ),
    EMPTY_CATALOG(
        listOf(
            "The Krate is empty for now.",
            "Nothing on the shelves yet.",
        ),
    ),
}

object KrateVoice {
    private const val PREFS = "krate_voice"
    private const val KEY_LAST_GREETING = "last_greeting"
    private const val TIME_OF_DAY_ODDS = 3

    private val picker = LinePicker()

    fun line(moment: Moment): String = picker.pick(moment.name, moment.lines)

    // one greeting per launch, never the same as the previous launch's - a time-of-day line about a third of the time
    fun greeting(
        context: Context,
        hour: Int = LocalTime.now().hour,
        random: Random = Random.Default,
    ): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val moment = if (random.nextInt(TIME_OF_DAY_ODDS) == 0) timeOfDay(hour) else Moment.GREETING
        val line = picker.pick(moment.name, moment.lines, avoid = prefs.getString(KEY_LAST_GREETING, null))
        prefs.edit().putString(KEY_LAST_GREETING, line).apply()
        return line
    }

    @Suppress("MagicNumber")
    internal fun timeOfDay(hour: Int): Moment =
        when (hour) {
            in 5..11 -> Moment.GREETING_MORNING
            in 12..16 -> Moment.GREETING_AFTERNOON
            in 17..21 -> Moment.GREETING_EVENING
            else -> Moment.GREETING_NIGHT
        }
}

// a line that stays put while the screen is up (and across tab switches), re-picked only when [key] changes
@Composable
fun rememberKrateLine(
    moment: Moment,
    key: Any? = null,
): String = rememberSaveable(moment, key) { KrateVoice.line(moment) }
