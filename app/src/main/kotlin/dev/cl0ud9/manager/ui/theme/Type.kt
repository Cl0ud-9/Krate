package dev.cl0ud9.manager.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.cl0ud9.manager.R

// Google Sans Flex (SIL OFL, licenses/google-sans-flex-OFL.txt) with its roundness axis fully on
private const val ROUNDED = 100f
private const val WIDE = 151f
private val FAMILY_WEIGHTS =
    listOf(
        FontWeight.Light,
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold,
        FontWeight.Black,
    )

@OptIn(ExperimentalTextApi::class)
private fun googleSansFlex(width: Float? = null): FontFamily =
    FontFamily(
        FAMILY_WEIGHTS.map { weight ->
            val rounded = FontVariation.Setting("ROND", ROUNDED)
            val settings =
                if (width == null) {
                    FontVariation.Settings(FontVariation.weight(weight.weight), rounded)
                } else {
                    FontVariation.Settings(FontVariation.weight(weight.weight), rounded, FontVariation.width(width))
                }
            Font(R.font.google_sans_flex, weight = weight, variationSettings = settings)
        },
    )

// the one family used across the app
val KrateRounded: FontFamily = googleSansFlex()

// the same face at its widest, for big expressive display lines like the setup welcome and What's new title
val KrateWide: FontFamily = googleSansFlex(width = WIDE)

// the big title heading each tab
val ManagerHeroTitle =
    TextStyle(
        fontFamily = KrateRounded,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = 1.sp,
    )

// wide display text for moments that deserve it
val KrateWideDisplay =
    TextStyle(
        fontFamily = KrateWide,
        fontWeight = FontWeight.SemiBold,
        fontSize = 42.sp,
        lineHeight = 1.1.em,
        letterSpacing = (-0.02).em,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

private fun style(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Double = 0.0,
) = TextStyle(
    fontFamily = KrateRounded,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

@Suppress("MagicNumber")
val ManagerTypography =
    Typography(
        displayLarge = style(FontWeight.Bold, 48, 56),
        displayMedium = style(FontWeight.Bold, 36, 44),
        displaySmall = style(FontWeight.Normal, 30, 38),
        headlineLarge = style(FontWeight.SemiBold, 32, 40),
        headlineMedium = style(FontWeight.SemiBold, 28, 36),
        headlineSmall = style(FontWeight.SemiBold, 24, 32),
        titleLarge = style(FontWeight.Normal, 22, 28),
        titleMedium = style(FontWeight.Medium, 18, 24, 0.15),
        titleSmall = style(FontWeight.Medium, 14, 20, 0.1),
        bodyLarge = style(FontWeight.Normal, 16, 24, 0.5),
        bodyMedium = style(FontWeight.Normal, 14, 20, 0.25),
        bodySmall = style(FontWeight.Normal, 12, 16, 0.4),
        labelLarge = style(FontWeight.Medium, 16, 20, 0.1),
        labelMedium = style(FontWeight.Medium, 14, 16, 0.5),
        labelSmall = style(FontWeight.Medium, 11, 16, 0.5),
    )
