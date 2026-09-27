package dev.cl0ud9.krate.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.cl0ud9.krate.ui.theme.harmonizedWith

private const val DARK_SURFACE_LUMINANCE = 0.5f

// each settings category gets its own hue so the list scans at a glance; a deep container with a light glyph
// in dark mode, a pale container with a deep glyph in light mode
@Suppress("MagicNumber")
internal enum class SettingsTint(
    private val darkBadge: Long,
    private val darkGlyph: Long,
    private val lightBadge: Long,
    private val lightGlyph: Long,
) {
    ROSE(0xFF7A2E4A, 0xFFFFD9E3, 0xFFFFD9E3, 0xFF5C1130),
    GREEN(0xFF1E5130, 0xFFB7F1C3, 0xFFC6F0CE, 0xFF0B3A1C),
    INDIGO(0xFF3A4374, 0xFFDEE0FF, 0xFFDEE0FF, 0xFF232C5C),
    AMBER(0xFF6B4A00, 0xFFFFDEA6, 0xFFFFDEA6, 0xFF4A3300),
    TEAL(0xFF004F54, 0xFF9CF0F6, 0xFFB2EEF3, 0xFF00363A),
    BLUE(0xFF004A77, 0xFFCDE5FF, 0xFFCDE5FF, 0xFF00344F),
    SLATE(0xFF3F474D, 0xFFDEE3EB, 0xFFE3E7EE, 0xFF2C3136),
    ;

    // harmonized with the wallpaper's primary, so the hues shift with Material You yet keep their own identity
    @Composable
    fun colors(): SettingsRowColors {
        val scheme = MaterialTheme.colorScheme
        val dark = scheme.surface.luminance() < DARK_SURFACE_LUMINANCE
        val (badge, glyph) = if (dark) darkBadge to darkGlyph else lightBadge to lightGlyph
        return SettingsRowColors(
            Color(badge).harmonizedWith(scheme.primary),
            Color(glyph).harmonizedWith(scheme.primary),
        )
    }
}
