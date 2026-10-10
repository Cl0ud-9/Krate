package dev.cl0ud9.krate.voice

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlin.random.Random

// lines that read the situation instead of a pool alone: whether the phone is online, and how long it's been all
// caught up or how many downloads Krate has checked
private const val SITUATION_ODDS = 3
private const val STREAK_DAYS = 3
private const val TRUST_DOWNLOADS = 10

// a refresh that failed: a word on being offline when the phone has no connection at all
fun refreshFailedLine(context: Context): String {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val online =
        connectivity
            ?.getNetworkCapabilities(connectivity.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    return KrateVoice.line(if (online) Moment.REFRESH_FAILED else Moment.OFFLINE)
}

// the headline over "you're all caught up": now and then the streak, or how many downloads Krate has checked
internal fun caughtUpLine(
    nowMillis: Long = System.currentTimeMillis(),
    random: Random = Random.Default,
): String {
    val days = KrateMemory.caughtUpDays(nowMillis)
    val checked = KrateMemory.verifiedDownloads
    return when {
        days >= STREAK_DAYS && random.nextInt(SITUATION_ODDS) == 0 -> "Caught up $days days running."
        checked >= TRUST_DOWNLOADS && random.nextInt(SITUATION_ODDS) == 0 ->
            "$checked downloads, every one checked."
        else -> KrateVoice.pick(Moment.ALL_CAUGHT_UP)
    }
}
