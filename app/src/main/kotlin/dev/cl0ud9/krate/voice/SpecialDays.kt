package dev.cl0ud9.krate.voice

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId

// New Year, Christmas and each yearly anniversary of installing Krate get their own greeting for the whole day
@Suppress("MagicNumber")
internal fun specialDay(
    today: LocalDate,
    installedOn: LocalDate?,
): Moment? =
    when {
        today.month == Month.JANUARY && today.dayOfMonth == 1 -> Moment.NEW_YEAR
        today.month == Month.DECEMBER && today.dayOfMonth == 25 -> Moment.CHRISTMAS
        installedOn != null &&
            today.year > installedOn.year &&
            today.month == installedOn.month &&
            today.dayOfMonth == installedOn.dayOfMonth -> Moment.KRATE_ANNIVERSARY
        else -> null
    }

internal fun installedOn(context: Context): LocalDate? =
    runCatching {
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        Instant.ofEpochMilli(installed).atZone(ZoneId.systemDefault()).toLocalDate()
    }.getOrNull()
