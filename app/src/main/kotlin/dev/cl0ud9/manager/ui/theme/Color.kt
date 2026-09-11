package dev.cl0ud9.manager.ui.theme

import androidx.compose.ui.graphics.Color

// Brand seed. Every color below is derived from this one value - never hand-edit a role individually,
// regenerate the whole block instead (see the note above ManagerLightColors).
val BrandPrimary = Color(0xFF3B5BFF)

// Full M3 tonal-palette fallback for when dynamic color isn't available (Android <12, or disabled) -
// section 41 of the spec targets Android 11+, so this isn't an edge case, it's the baseline experience
// for a real slice of supported devices. Without this, lightColorScheme(primary = BrandPrimary) /
// darkColorScheme(primary = BrandPrimaryDark) only override the `primary` role and leave every other
// role (secondary, tertiary, surface containers, ...) on Compose's default baseline-purple scheme -
// visibly unrelated to the brand color everywhere except the one primary-colored button.
//
// Generated (not hand-picked) with Google's own reference algorithm - the same HCT/CAM16 math that
// powers dynamicLightColorScheme/dynamicDarkColorScheme on-device - via the official
// @material/material-color-utilities npm package, SchemeTonalSpot variant (Android's default Material
// You look), seed #3B5BFF, contrast level 0. That's a one-off generation tool, not an app dependency:
// nothing under node_modules ships in the app, only these resulting constants do. To regenerate after
// changing the brand seed: `npm i @material/material-color-utilities`, then
//   import { MaterialDynamicColors, SchemeTonalSpot, Hct, argbFromHex, hexFromArgb }
//     from '@material/material-color-utilities'
//   new SchemeTonalSpot(Hct.fromInt(argbFromHex('#RRGGBB')), isDark, 0.0)
// and read every role off `new MaterialDynamicColors()` the same way dynamicLightColorScheme does.

// --- Light scheme ---
val LightPrimary = Color(0xFF515B92)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFDEE0FF)
val LightOnPrimaryContainer = Color(0xFF394379)
val LightInversePrimary = Color(0xFFBAC3FF)
val LightPrimaryFixed = Color(0xFFDEE0FF)
val LightPrimaryFixedDim = Color(0xFFBAC3FF)
val LightOnPrimaryFixed = Color(0xFF0B154B)
val LightOnPrimaryFixedVariant = Color(0xFF394379)
val LightSecondary = Color(0xFF5B5D72)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE0E1F9)
val LightOnSecondaryContainer = Color(0xFF434659)
val LightSecondaryFixed = Color(0xFFE0E1F9)
val LightSecondaryFixedDim = Color(0xFFC3C5DD)
val LightOnSecondaryFixed = Color(0xFF181A2C)
val LightOnSecondaryFixedVariant = Color(0xFF434659)
val LightTertiary = Color(0xFF77536D)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFFD7F1)
val LightOnTertiaryContainer = Color(0xFF5D3C55)
val LightTertiaryFixed = Color(0xFFFFD7F1)
val LightTertiaryFixedDim = Color(0xFFE5BAD7)
val LightOnTertiaryFixed = Color(0xFF2D1228)
val LightOnTertiaryFixedVariant = Color(0xFF5D3C55)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF93000A)
val LightBackground = Color(0xFFFBF8FF)
val LightOnBackground = Color(0xFF1B1B21)
val LightSurface = Color(0xFFFBF8FF)
val LightOnSurface = Color(0xFF1B1B21)
val LightSurfaceVariant = Color(0xFFE3E1EC)
val LightOnSurfaceVariant = Color(0xFF46464F)
val LightSurfaceTint = Color(0xFF515B92)
val LightInverseSurface = Color(0xFF303036)
val LightInverseOnSurface = Color(0xFFF2EFF7)
val LightOutline = Color(0xFF767680)
val LightOutlineVariant = Color(0xFFC7C5D0)
val LightScrim = Color(0xFF000000)
val LightSurfaceBright = Color(0xFFFBF8FF)
val LightSurfaceDim = Color(0xFFDBD9E0)
val LightSurfaceContainer = Color(0xFFEFEDF4)
val LightSurfaceContainerHigh = Color(0xFFE9E7EF)
val LightSurfaceContainerHighest = Color(0xFFE4E1E9)
val LightSurfaceContainerLow = Color(0xFFF5F2FA)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)

// --- Dark scheme ---
val DarkPrimary = Color(0xFFBAC3FF)
val DarkOnPrimary = Color(0xFF222C61)
val DarkPrimaryContainer = Color(0xFF394379)
val DarkOnPrimaryContainer = Color(0xFFDEE0FF)
val DarkInversePrimary = Color(0xFF515B92)
val DarkPrimaryFixed = Color(0xFFDEE0FF)
val DarkPrimaryFixedDim = Color(0xFFBAC3FF)
val DarkOnPrimaryFixed = Color(0xFF0B154B)
val DarkOnPrimaryFixedVariant = Color(0xFF394379)
val DarkSecondary = Color(0xFFC3C5DD)
val DarkOnSecondary = Color(0xFF2D2F42)
val DarkSecondaryContainer = Color(0xFF434659)
val DarkOnSecondaryContainer = Color(0xFFE0E1F9)
val DarkSecondaryFixed = Color(0xFFE0E1F9)
val DarkSecondaryFixedDim = Color(0xFFC3C5DD)
val DarkOnSecondaryFixed = Color(0xFF181A2C)
val DarkOnSecondaryFixedVariant = Color(0xFF434659)
val DarkTertiary = Color(0xFFE5BAD7)
val DarkOnTertiary = Color(0xFF44263D)
val DarkTertiaryContainer = Color(0xFF5D3C55)
val DarkOnTertiaryContainer = Color(0xFFFFD7F1)
val DarkTertiaryFixed = Color(0xFFFFD7F1)
val DarkTertiaryFixedDim = Color(0xFFE5BAD7)
val DarkOnTertiaryFixed = Color(0xFF2D1228)
val DarkOnTertiaryFixedVariant = Color(0xFF5D3C55)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkBackground = Color(0xFF121318)
val DarkOnBackground = Color(0xFFE4E1E9)
val DarkSurface = Color(0xFF121318)
val DarkOnSurface = Color(0xFFE4E1E9)
val DarkSurfaceVariant = Color(0xFF46464F)
val DarkOnSurfaceVariant = Color(0xFFC7C5D0)
val DarkSurfaceTint = Color(0xFFBAC3FF)
val DarkInverseSurface = Color(0xFFE4E1E9)
val DarkInverseOnSurface = Color(0xFF303036)
val DarkOutline = Color(0xFF90909A)
val DarkOutlineVariant = Color(0xFF46464F)
val DarkScrim = Color(0xFF000000)
val DarkSurfaceBright = Color(0xFF39393F)
val DarkSurfaceDim = Color(0xFF121318)
val DarkSurfaceContainer = Color(0xFF1F1F25)
val DarkSurfaceContainerHigh = Color(0xFF29292F)
val DarkSurfaceContainerHighest = Color(0xFF34343A)
val DarkSurfaceContainerLow = Color(0xFF1B1B21)
val DarkSurfaceContainerLowest = Color(0xFF0D0E13)
