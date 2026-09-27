package dev.cl0ud9.manager.platform.notifications

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import dev.cl0ud9.manager.R

// Krate's blue, for Android 11 where there are no wallpaper colors to follow
private const val KRATE_ACCENT = 0xFF5470FF.toInt()

// the rocket badge and app name color: the wallpaper's accent on Android 12+, like the rest of Krate
internal fun krateAccent(context: Context): Int =
    if (Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.S
    ) {
        context.getColor(android.R.color.system_accent1_500)
    } else {
        KRATE_ACCENT
    }

private fun bold(text: String): CharSequence =
    SpannableString(text).apply { setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }

// every Krate notification looks the same: the rocket in Krate's color, a bold headline, the plain fact under it,
// and a small label for what kind of news it is ("Krate • Updates • now")
internal fun NotificationCompat.Builder.krateContent(
    context: Context,
    headline: String,
    fact: String,
    category: String,
): NotificationCompat.Builder =
    setSmallIcon(R.drawable.ic_stat_krate)
        .setColor(krateAccent(context))
        .setSubText(category)
        .setContentTitle(bold(headline))
        .setContentText(fact)
        .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(bold(headline)).bigText(fact))
