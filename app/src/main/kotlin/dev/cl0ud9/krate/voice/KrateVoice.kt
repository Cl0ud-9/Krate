package dev.cl0ud9.krate.voice

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

// Krate's voice: every playful line lives here, one pool per moment, plus the plain line for when playful is off.
// A line is only ever the headline - the plain facts (which app, what happened, what to do) always sit next to it
enum class Moment(
    val plain: String,
    val lines: List<String>,
) {
    GREETING(
        "Welcome back.",
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
        "Good morning.",
        listOf(
            "Good morning, Krate keeper.",
            "Rise and Krate.",
            "Morning! Coffee first?",
            "Fresh morning, fresh apps.",
            "Early bird gets the updates.",
        ),
    ),
    GREETING_AFTERNOON(
        "Good afternoon.",
        listOf(
            "Good afternoon.",
            "Afternoon check-in?",
            "Unpacking on your lunch break?",
            "Halfway through the day already.",
        ),
    ),
    GREETING_EVENING(
        "Good evening.",
        listOf(
            "Good evening.",
            "Evening, Krate keeper.",
            "Winding down?",
            "One last look before tonight?",
        ),
    ),
    GREETING_NIGHT(
        "Welcome back.",
        listOf(
            "Hello, night owl.",
            "Burning the midnight oil?",
            "Up late, huh?",
            "The Krate never sleeps.",
        ),
    ),

    // special days take over the greeting for the whole day
    NEW_YEAR(
        "Happy New Year.",
        listOf(
            "Happy New Year, Krate keeper!",
            "New year, fresh Krate.",
            "Out with the old versions.",
            "A whole year of updates ahead.",
        ),
    ),
    CHRISTMAS(
        "Merry Christmas.",
        listOf(
            "Merry Christmas, Krate keeper!",
            "Unwrapping season.",
            "Something new under the tree?",
            "Ho ho ho, fresh apps.",
        ),
    ),
    KRATE_ANNIVERSARY(
        "Happy Krate anniversary.",
        listOf(
            "Happy Krate-iversary!",
            "Another year in the Krate.",
            "Thanks for sticking around!",
            "A year of fresh apps together.",
        ),
    ),
    ALL_CAUGHT_UP(
        "You're all caught up.",
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
        "Updates available.",
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
        "Krate update available.",
        listOf(
            "A fresh Krate is ready.",
            "Krate got an upgrade.",
            "Time for a fresh Krate.",
            "Something new for the Krate itself.",
        ),
    ),
    KRATE_UPDATED(
        "Krate updated.",
        listOf(
            "Freshly unpacked.",
            "Upgrade complete.",
            "Krate is back, and better.",
            "New Krate, same you.",
        ),
    ),
    KRATE_LATEST(
        "Up to date.",
        listOf(
            "Freshest Krate there is.",
            "Nothing newer yet.",
            "Still the newest Krate.",
        ),
    ),
    CHECKING_FOR_UPDATES(
        "Checking for updates...",
        listOf(
            "Checking for updates...",
            "Peeking for a newer Krate...",
            "Checking the shelves for updates...",
            "Looking for a fresh Krate...",
        ),
    ),
    INSTALLED(
        "Installed.",
        listOf(
            "Into the Krate it goes.",
            "And... it's in.",
            "All tucked in.",
            "Successfully packed.",
            "Ready to roll.",
        ),
    ),
    PARTLY_INSTALLED(
        "Partly installed.",
        listOf(
            "Mostly packed.",
            "Nearly everything made it in.",
            "Almost there.",
        ),
    ),
    INSTALL_FAILED(
        "Install failed.",
        listOf(
            "That one escaped the Krate.",
            "That didn't quite fit.",
            "Oops. That didn't make it in.",
            "Well... that didn't go to plan.",
        ),
    ),
    DOWNLOADED(
        "Download complete.",
        listOf(
            "Packed and ready.",
            "That's safely in the Krate.",
            "Download complete. Nice.",
            "One more thing packed.",
            "Freshly downloaded.",
        ),
    ),
    DOWNLOAD_FAILED(
        "Download failed.",
        listOf(
            "That download slipped away.",
            "The download hit a snag.",
            "That one got lost on the way.",
            "Well... that didn't go to plan.",
        ),
    ),
    REFRESH_FAILED(
        "Couldn't refresh.",
        listOf(
            "The Krate couldn't check in.",
            "The Krate lost the trail.",
            "That didn't get through.",
            "The Krate needs a little internet.",
        ),
    ),
    NOTHING_UNPACKED(
        "Nothing here yet.",
        listOf(
            "Nothing unpacked yet.",
            "A fresh, empty Krate.",
        ),
    ),
    EMPTY_CATALOG(
        "No apps yet.",
        listOf(
            "The Krate is empty for now.",
            "Nothing on the shelves yet.",
        ),
    ),

    // the line under "Welcome to Krate" in the intro right after setup
    WELCOME(
        "Your apps, straight from the source.",
        listOf(
            "No Play Store was harmed in this Krate.",
            "All packed. Mind the bubble wrap.",
            "Straight from the source. No middlemen.",
            "Your apps, minus the waiting in line.",
        ),
    ),

    // Settings > GitHub access, once a saved token has been checked
    INVITE_UNLOCKED(
        "Invite-only apps unlocked.",
        listOf(
            "You're on the list.",
            "The back room is open.",
            "Invite accepted. Come on in.",
            "Welcome to the secret shelf.",
            "VIP shelf unlocked.",
        ),
    ),
    TOKEN_REJECTED(
        "Token not accepted.",
        listOf(
            "That key doesn't fit.",
            "The back room stayed locked.",
            "No luck with that key.",
            "Hmm, that token opened nothing.",
        ),
    ),
    INVITE_UNREACHABLE(
        "Couldn't check the token.",
        listOf(
            "The back room isn't answering.",
            "Couldn't knock on the back room.",
            "No answer from the back room.",
        ),
    ),
}

object KrateVoice {
    private const val PREFS = "krate_voice"
    private const val KEY_LAST_GREETING = "last_greeting"
    private const val KEY_PLAYFUL = "playful"
    private const val TIME_OF_DAY_ODDS = 3

    private val picker = LinePicker()

    // off swaps every playful line for its plain one; Compose state, so open screens switch straight away
    var playful by mutableStateOf(true)
        private set

    fun load(context: Context) {
        playful = prefs(context).getBoolean(KEY_PLAYFUL, true)
    }

    fun setPlayful(
        context: Context,
        enabled: Boolean,
    ) {
        playful = enabled
        prefs(context).edit().putBoolean(KEY_PLAYFUL, enabled).apply()
    }

    fun line(moment: Moment): String = if (playful) pick(moment) else moment.plain

    // always a playful line, for screens that remember one and choose between it and the plain line as the toggle moves
    fun pick(moment: Moment): String = picker.pick(moment.name, moment.lines)

    // the playful line in front of the fact, or just the fact when playful messages are off
    fun leadIn(
        moment: Moment,
        fact: String,
    ): String = if (playful) "${line(moment)} $fact" else fact

    // a special day's pool when there is one, otherwise a time-of-day line about a third of the time
    fun greetingMoment(
        context: Context,
        today: LocalDate = LocalDate.now(),
        hour: Int = LocalTime.now().hour,
        random: Random = Random.Default,
    ): Moment =
        specialDay(today, installedOn(context))
            ?: if (random.nextInt(TIME_OF_DAY_ODDS) == 0) timeOfDay(hour) else Moment.GREETING

    // one greeting per launch, never the same as the previous launch's
    fun greeting(
        context: Context,
        moment: Moment,
    ): String {
        val prefs = prefs(context)
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

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

// a line that stays put while the screen is up (and across tab switches), re-picked only when [key] changes
@Composable
fun rememberKrateLine(
    moment: Moment,
    key: Any? = null,
): String {
    val line = rememberSaveable(moment, key) { KrateVoice.pick(moment) }
    return if (KrateVoice.playful) line else moment.plain
}

// the fact with a playful line in front, or the bare fact when playful messages are off
@Composable
fun rememberKrateLeadIn(
    moment: Moment,
    fact: String,
    key: Any? = null,
): String {
    val line = rememberKrateLine(moment, key)
    return if (KrateVoice.playful) "$line $fact" else fact
}

// picked once per launch; saved state keeps it through tab switches and rotation
@Composable
fun rememberKrateGreeting(): String {
    val context = LocalContext.current
    val moment = rememberSaveable { KrateVoice.greetingMoment(context) }
    val line = rememberSaveable(moment) { KrateVoice.greeting(context, moment) }
    return if (KrateVoice.playful) line else moment.plain
}
