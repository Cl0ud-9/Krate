package dev.cl0ud9.manager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.cl0ud9.manager.domain.model.ThemeMode

// full brand-seeded tonal palettes - every role, not just primary - see the generation note in Color.kt
private val LightColors =
    lightColorScheme(
        primary = LightPrimary,
        onPrimary = LightOnPrimary,
        primaryContainer = LightPrimaryContainer,
        onPrimaryContainer = LightOnPrimaryContainer,
        inversePrimary = LightInversePrimary,
        primaryFixed = LightPrimaryFixed,
        primaryFixedDim = LightPrimaryFixedDim,
        onPrimaryFixed = LightOnPrimaryFixed,
        onPrimaryFixedVariant = LightOnPrimaryFixedVariant,
        secondary = LightSecondary,
        onSecondary = LightOnSecondary,
        secondaryContainer = LightSecondaryContainer,
        onSecondaryContainer = LightOnSecondaryContainer,
        secondaryFixed = LightSecondaryFixed,
        secondaryFixedDim = LightSecondaryFixedDim,
        onSecondaryFixed = LightOnSecondaryFixed,
        onSecondaryFixedVariant = LightOnSecondaryFixedVariant,
        tertiary = LightTertiary,
        onTertiary = LightOnTertiary,
        tertiaryContainer = LightTertiaryContainer,
        onTertiaryContainer = LightOnTertiaryContainer,
        tertiaryFixed = LightTertiaryFixed,
        tertiaryFixedDim = LightTertiaryFixedDim,
        onTertiaryFixed = LightOnTertiaryFixed,
        onTertiaryFixedVariant = LightOnTertiaryFixedVariant,
        error = LightError,
        onError = LightOnError,
        errorContainer = LightErrorContainer,
        onErrorContainer = LightOnErrorContainer,
        background = LightBackground,
        onBackground = LightOnBackground,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceVariant,
        surfaceTint = LightSurfaceTint,
        inverseSurface = LightInverseSurface,
        inverseOnSurface = LightInverseOnSurface,
        outline = LightOutline,
        outlineVariant = LightOutlineVariant,
        scrim = LightScrim,
        surfaceBright = LightSurfaceBright,
        surfaceDim = LightSurfaceDim,
        surfaceContainer = LightSurfaceContainer,
        surfaceContainerHigh = LightSurfaceContainerHigh,
        surfaceContainerHighest = LightSurfaceContainerHighest,
        surfaceContainerLow = LightSurfaceContainerLow,
        surfaceContainerLowest = LightSurfaceContainerLowest,
    )

private val DarkColors =
    darkColorScheme(
        primary = DarkPrimary,
        onPrimary = DarkOnPrimary,
        primaryContainer = DarkPrimaryContainer,
        onPrimaryContainer = DarkOnPrimaryContainer,
        inversePrimary = DarkInversePrimary,
        primaryFixed = DarkPrimaryFixed,
        primaryFixedDim = DarkPrimaryFixedDim,
        onPrimaryFixed = DarkOnPrimaryFixed,
        onPrimaryFixedVariant = DarkOnPrimaryFixedVariant,
        secondary = DarkSecondary,
        onSecondary = DarkOnSecondary,
        secondaryContainer = DarkSecondaryContainer,
        onSecondaryContainer = DarkOnSecondaryContainer,
        secondaryFixed = DarkSecondaryFixed,
        secondaryFixedDim = DarkSecondaryFixedDim,
        onSecondaryFixed = DarkOnSecondaryFixed,
        onSecondaryFixedVariant = DarkOnSecondaryFixedVariant,
        tertiary = DarkTertiary,
        onTertiary = DarkOnTertiary,
        tertiaryContainer = DarkTertiaryContainer,
        onTertiaryContainer = DarkOnTertiaryContainer,
        tertiaryFixed = DarkTertiaryFixed,
        tertiaryFixedDim = DarkTertiaryFixedDim,
        onTertiaryFixed = DarkOnTertiaryFixed,
        onTertiaryFixedVariant = DarkOnTertiaryFixedVariant,
        error = DarkError,
        onError = DarkOnError,
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkOnErrorContainer,
        background = DarkBackground,
        onBackground = DarkOnBackground,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        surfaceTint = DarkSurfaceTint,
        inverseSurface = DarkInverseSurface,
        inverseOnSurface = DarkInverseOnSurface,
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
        scrim = DarkScrim,
        surfaceBright = DarkSurfaceBright,
        surfaceDim = DarkSurfaceDim,
        surfaceContainer = DarkSurfaceContainer,
        surfaceContainerHigh = DarkSurfaceContainerHigh,
        surfaceContainerHighest = DarkSurfaceContainerHighest,
        surfaceContainerLow = DarkSurfaceContainerLow,
        surfaceContainerLowest = DarkSurfaceContainerLowest,
    )

// squircle shapes throughout, for the expressive look
private val ManagerShapes =
    Shapes(
        extraSmall = ShapeCache.smooth8,
        small = ShapeCache.smooth12,
        medium = ShapeCache.smooth16,
        large = ShapeCache.smooth28,
        extraLarge = ShapeCache.smooth32,
    )

// requires material3 1.5.0-alpha (see gradle/libs.versions.toml) - MaterialExpressiveTheme and
// MotionScheme are Kotlin `internal` on the 1.4.x stable line this project used until now, so this
// wasn't reachable at all until that pin landed, not merely gated behind the opt-in below
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ManagerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme =
        when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }

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

    // expressive() over standard() - springier, more energetic defaults for stock M3 components'
    // own built-in animations (Switch, dialogs, etc.), matching the hand-rolled spring motion already
    // used for the nav bar and screen transitions rather than fighting it with calmer stock timing
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ManagerShapes,
        typography = ManagerTypography,
        content = content,
    )
}
