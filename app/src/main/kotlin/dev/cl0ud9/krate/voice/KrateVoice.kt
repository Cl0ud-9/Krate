package dev.cl0ud9.krate.voice

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZonedDateTime
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
            "Look who's here.",
            "Krate is awake.",
            "Krate is ready.",
            "Back for more?",
            "Ready when you are.",
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
            "Unboxing time.",
            "Knock knock. Updates?",
            "Pop the lid.",
            "Let's peek inside.",
            "Your shelf, your rules.",
            "Handle with care.",
            "This side up.",
            "Mind the packing peanuts.",
            "No middlemen here.",
            "The Krate is humming.",
            "Fresh from the source.",
        ),
    ),
    QUICK_RETURN(
        "Welcome back.",
        listOf(
            "Back already?",
            "Forgot something?",
            "Miss me already?",
            "That was quick.",
            "Round two?",
            "Didn't we just meet?",
        ),
    ),
    LONG_ABSENCE(
        "Welcome back.",
        listOf(
            "Long time no see!",
            "The Krate missed you.",
            "It's been a while.",
            "Welcome back, stranger.",
            "Blowing the dust off.",
            "You're back! It's been ages.",
        ),
    ),
    WEEKEND(
        "Welcome back.",
        listOf(
            "Weekend unpacking?",
            "Happy weekend, Krate keeper!",
            "Weekend mode: on.",
            "No work, just apps.",
            "A lazy weekend check-in?",
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
            "Up and at 'em.",
            "Morning, sunshine.",
        ),
    ),
    GREETING_AFTERNOON(
        "Good afternoon.",
        listOf(
            "Good afternoon.",
            "Afternoon check-in?",
            "Unpacking on your lunch break?",
            "Halfway through the day already.",
            "Post-lunch unpacking?",
            "Afternoon, Krate keeper.",
        ),
    ),
    GREETING_EVENING(
        "Good evening.",
        listOf(
            "Good evening.",
            "Evening, Krate keeper.",
            "Winding down?",
            "One last look before tonight?",
            "Evening unpacking?",
            "Dinner done? Apps next.",
        ),
    ),
    GREETING_NIGHT(
        "Welcome back.",
        listOf(
            "Hello, night owl.",
            "Burning the midnight oil?",
            "Up late, huh?",
            "The Krate never sleeps.",
            "Still up?",
            "Midnight snack? Fresh apps.",
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
            "Fresh coat of paint.",
        ),
    ),
    KRATE_LATEST(
        "Up to date.",
        listOf(
            "Freshest Krate there is.",
            "Nothing newer yet.",
            "Still the newest Krate.",
            "You've got the newest one.",
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
            "Most of it made it in.",
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
            "Clean slate so far.",
            "Nothing here... yet.",
        ),
    ),
    EMPTY_CATALOG(
        "No apps yet.",
        listOf(
            "The Krate is empty for now.",
            "Nothing on the shelves yet.",
            "Empty shelves, for now.",
            "Awaiting the first delivery.",
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
            "The back room is quiet right now.",
        ),
    ),
}

object KrateVoice {
    private const val PREFS = "krate_voice"
    private const val KEY_PLAYFUL = "playful"
    private const val KEY_LAST_OPEN = "last_open"

    private val picker = LinePicker()

    // off swaps every playful line for its plain one; Compose state, so open screens switch straight away
    var playful by mutableStateOf(true)
        private set

    // run once per process: the switch, and the used-lines memory that keeps pools from repeating across launches
    fun load(context: Context) {
        val prefs = prefs(context)
        playful = prefs.getBoolean(KEY_PLAYFUL, true)
        picker.memory = PrefsLineMemory(prefs)
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

    // once per launch: reads how long it's been since the last one, then records this one
    fun greetingMoment(
        context: Context,
        now: ZonedDateTime = ZonedDateTime.now(),
        random: Random = Random.Default,
    ): Moment {
        val prefs = prefs(context)
        val lastOpen = prefs.getLong(KEY_LAST_OPEN, 0L).takeIf { it > 0L }
        val nowMillis = now.toInstant().toEpochMilli()
        prefs.edit().putLong(KEY_LAST_OPEN, nowMillis).apply()
        val lastOpenDate = lastOpen?.let { Instant.ofEpochMilli(it).atZone(now.zone).toLocalDate() }
        return greetingMomentFor(
            GreetingContext(
                today = now.toLocalDate(),
                hour = now.hour,
                sinceLastOpenMillis = lastOpen?.let { nowMillis - it },
                firstOpenToday = lastOpenDate != now.toLocalDate(),
                installedOn = installedOn(context),
            ),
            random,
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

// each pool's used lines as one newline-joined string, so a pool keeps its place across launches
private class PrefsLineMemory(
    private val prefs: SharedPreferences,
) : LineMemory {
    override fun used(key: String): List<String> =
        prefs
            .getString("used_$key", null)
            ?.split('\n')
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    override fun save(
        key: String,
        used: List<String>,
    ) {
        prefs.edit().putString("used_$key", used.joinToString("\n")).apply()
    }
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
    val line = rememberSaveable(moment) { KrateVoice.pick(moment) }
    return if (KrateVoice.playful) line else moment.plain
}
