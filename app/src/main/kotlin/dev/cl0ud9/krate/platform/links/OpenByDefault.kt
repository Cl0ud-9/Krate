package dev.cl0ud9.krate.platform.links

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import dev.cl0ud9.krate.domain.links.LinkState

// Android's "Open by default" for one app: which of its web addresses open in it. Reading it and opening its page work
// on Android 12 and newer; before that there's no per-address control to read or open
object OpenByDefault {
    val supported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // every address the app declares, with whether it's switched on; null where Android can't say
    fun states(
        context: Context,
        packageName: String,
    ): List<LinkState>? = if (supported) readStates(context, packageName) else null

    @RequiresApi(Build.VERSION_CODES.S)
    private fun readStates(
        context: Context,
        packageName: String,
    ): List<LinkState>? {
        val manager = context.getSystemService(DomainVerificationManager::class.java)
        val state = runCatching { manager?.getDomainVerificationUserState(packageName) }.getOrNull()
        return state?.hostToStateMap?.map { (host, value) ->
            LinkState(host, on = value != DomainVerificationUserState.DOMAIN_STATE_NONE)
        }
    }

    // whether another app already opens this address by default: when the phone sends it somewhere other than where it
    // sends an address nobody owns (the browser, or the chooser), someone does. Unreadable counts as taken, so Krate
    // never offers to switch an address it can't be sure is free
    fun takenByAnother(
        context: Context,
        host: String,
        packageName: String,
    ): Boolean {
        val unowned = openedBy(context, "https://unowned.invalid/")
        val now = openedBy(context, "https://$host/") ?: return true
        return now != packageName && now != unowned && now != "android"
    }

    // the app that opens this address now, or null when nobody in particular does (the browser, the chooser, no answer)
    fun ownerOf(
        context: Context,
        host: String,
    ): String? {
        val unowned = openedBy(context, "https://unowned.invalid/")
        val now = openedBy(context, "https://$host/") ?: return null
        return now.takeIf { it != unowned && it != "android" }
    }

    private fun openedBy(
        context: Context,
        url: String,
    ): String? =
        runCatching {
            context.packageManager
                .resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
        }.getOrNull()

    fun intent(packageName: String): Intent =
        Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
