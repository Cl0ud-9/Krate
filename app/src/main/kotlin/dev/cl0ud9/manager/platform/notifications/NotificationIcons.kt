package dev.cl0ud9.manager.platform.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.core.graphics.drawable.toBitmap
import dev.cl0ud9.manager.domain.model.AppProfile

// a notification's large icon says whose news it is: Krate's own icon for Krate, a managed app's own icon for that app
object NotificationIcons {
    fun krate(context: Context): Bitmap? = installedIcon(context, context.packageName)

    // the icon the app has on this device, else the one the catalog ships for it
    fun app(
        context: Context,
        app: AppProfile,
    ): Bitmap? = installedIcon(context, app.packageName) ?: app.iconPng?.let(::decodePng)

    private fun installedIcon(
        context: Context,
        packageName: String,
    ): Bitmap? =
        runCatching {
            val size = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
            context.packageManager.getApplicationIcon(packageName).toBitmap(size, size)
        }.getOrNull()

    private fun decodePng(base64Png: String): Bitmap? =
        runCatching {
            val bytes = Base64.decode(base64Png, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
}
