package dev.cl0ud9.manager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(primary = BrandPrimary)
private val DarkColors = darkColorScheme(primary = BrandPrimaryDark)

// squircle shapes throughout, for the expressive look
private val ManagerShapes =
    Shapes(
        extraSmall = ShapeCache.smooth8,
        small = ShapeCache.smooth12,
        medium = ShapeCache.smooth16,
        large = ShapeCache.smooth28,
        extraLarge = ShapeCache.smooth32,
    )

@Composable
fun ManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    // dynamic color needs Android 12+, minSdk is 30 so fall back below that
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> {
                DarkColors
            }

            else -> {
                LightColors
            }
        }

    // MaterialExpressiveTheme/MotionScheme are internal in this resolved material3 version, not usable yet
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = ManagerShapes,
        typography = ManagerTypography,
        content = content,
    )
}
