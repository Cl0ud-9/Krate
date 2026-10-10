package dev.cl0ud9.krate.platform.packageinfo

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo

private const val FLAGS = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS

// what each sensitive permission lets an app reach, in plain words; several permissions share one, so going from rough
// to precise location, say, isn't news
private val AREAS =
    mapOf(
        Manifest.permission.ACCESS_FINE_LOCATION to "your location",
        Manifest.permission.ACCESS_COARSE_LOCATION to "your location",
        Manifest.permission.ACCESS_BACKGROUND_LOCATION to "your location in the background",
        Manifest.permission.CAMERA to "the camera",
        Manifest.permission.RECORD_AUDIO to "the microphone",
        Manifest.permission.READ_CONTACTS to "your contacts",
        Manifest.permission.WRITE_CONTACTS to "your contacts",
        Manifest.permission.GET_ACCOUNTS to "your contacts",
        Manifest.permission.READ_CALENDAR to "your calendar",
        Manifest.permission.WRITE_CALENDAR to "your calendar",
        Manifest.permission.READ_SMS to "your text messages",
        Manifest.permission.SEND_SMS to "your text messages",
        Manifest.permission.RECEIVE_SMS to "your text messages",
        Manifest.permission.RECEIVE_MMS to "your text messages",
        Manifest.permission.READ_CALL_LOG to "your call log",
        Manifest.permission.WRITE_CALL_LOG to "your call log",
        Manifest.permission.CALL_PHONE to "phone calls",
        Manifest.permission.READ_PHONE_STATE to "phone calls",
        Manifest.permission.READ_PHONE_NUMBERS to "phone calls",
        Manifest.permission.ANSWER_PHONE_CALLS to "phone calls",
        Manifest.permission.BODY_SENSORS to "body sensors",
        Manifest.permission.ACTIVITY_RECOGNITION to "your physical activity",
        Manifest.permission.READ_EXTERNAL_STORAGE to "your photos, videos and music",
        Manifest.permission.WRITE_EXTERNAL_STORAGE to "your photos, videos and music",
        "android.permission.READ_MEDIA_IMAGES" to "your photos, videos and music",
        "android.permission.READ_MEDIA_VIDEO" to "your photos, videos and music",
        "android.permission.READ_MEDIA_AUDIO" to "your photos, videos and music",
        "android.permission.MANAGE_EXTERNAL_STORAGE" to "all your files",
        "android.permission.BLUETOOTH_SCAN" to "nearby devices",
        "android.permission.BLUETOOTH_CONNECT" to "nearby devices",
        "android.permission.NEARBY_WIFI_DEVICES" to "nearby devices",
        Manifest.permission.SYSTEM_ALERT_WINDOW to "drawing over other apps",
        Manifest.permission.REQUEST_INSTALL_PACKAGES to "installing other apps",
        "android.permission.QUERY_ALL_PACKAGES" to "seeing every app on your phone",
        Manifest.permission.PACKAGE_USAGE_STATS to "how you use your apps",
        Manifest.permission.WRITE_SETTINGS to "changing system settings",
    )

// services and receivers an app can only switch on with you, but which reach further than any permission
private val BOUND_AREAS =
    mapOf(
        Manifest.permission.BIND_ACCESSIBILITY_SERVICE to "what's on your screen (accessibility)",
        Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE to "your notifications",
        Manifest.permission.BIND_DEVICE_ADMIN to "device admin",
        Manifest.permission.BIND_VPN_SERVICE to "your internet traffic (VPN)",
    )

// what a downloaded build can reach that the version on the phone can't; empty when it isn't installed, or either one
// can't be read
fun newSensitiveAreas(
    context: Context,
    packageName: String,
    apkPath: String,
): List<String> =
    runCatching {
        val pm = context.packageManager
        val installed = pm.getPackageInfo(packageName, FLAGS)
        val downloaded = pm.getPackageArchiveInfo(apkPath, FLAGS) ?: return@runCatching emptyList()
        (areasOf(pm, downloaded) - areasOf(pm, installed)).toList()
    }.getOrDefault(emptyList())

private fun areasOf(
    pm: PackageManager,
    info: PackageInfo,
): Set<String> {
    val asked =
        info.requestedPermissions.orEmpty().mapNotNull { permission ->
            AREAS[permission] ?: otherDangerous(pm, permission)
        }
    val bound =
        (info.services.orEmpty().map { it.permission } + info.receivers.orEmpty().map { it.permission })
            .mapNotNull { BOUND_AREAS[it] }
    return (asked + bound).toSet()
}

// a dangerous permission not in the list above still counts, by the name Android gives it
private fun otherDangerous(
    pm: PackageManager,
    permission: String,
): String? =
    runCatching {
        val info = pm.getPermissionInfo(permission, 0)
        val dangerous = info.protection == PermissionInfo.PROTECTION_DANGEROUS
        if (dangerous && permission != Manifest.permission.POST_NOTIFICATIONS) {
            info.loadLabel(pm).toString().lowercase()
        } else {
            null
        }
    }.getOrNull()

// "your location", "your location and the camera", "your location, the camera and the microphone"
fun joinAreas(areas: List<String>): String =
    when (areas.size) {
        0 -> ""
        1 -> areas[0]
        else -> areas.dropLast(1).joinToString(", ") + " and " + areas.last()
    }
