package com.resonance.player.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/**
 * The screenshots use a script/handwriting typeface. FontFamily.Cursive is the built-in stand-in;
 * to use an exact font, drop a .ttf into res/font and replace this with FontFamily(Font(R.font.your_font)).
 */
val AppFont: FontFamily = FontFamily.Cursive

data class AppPalette(val name: String, val top: Color, val bottom: Color, val accent: Color)

val Palettes = listOf(
    AppPalette("Night", Color(0xFF0B0D2A), Color(0xFF2A1B5C), Color(0xFF9B8CFF)),
    AppPalette("Ocean", Color(0xFF06141F), Color(0xFF0B4A6B), Color(0xFF5CC8FF)),
    AppPalette("Ember", Color(0xFF1A0B0B), Color(0xFF5A1E2E), Color(0xFFFF8A7A)),
    AppPalette("Forest", Color(0xFF07140D), Color(0xFF16452B), Color(0xFF6EE7A0)),
)

val LocalPalette = staticCompositionLocalOf { Palettes[0] }

@Composable
fun ResonanceTheme(palette: AppPalette, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = palette.accent,
        onPrimary = Color.Black,
        background = palette.top,
        surface = palette.top,
        onSurface = Color.White,
        onBackground = Color.White,
        surfaceVariant = Color.White.copy(alpha = 0.08f),
        secondaryContainer = palette.accent.copy(alpha = 0.35f),
        onSecondaryContainer = Color.White,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalPalette provides palette,
            LocalContentColor provides Color.White,
        ) {
            ProvideTextStyle(TextStyle(fontFamily = AppFont, color = Color.White)) {
                content()
            }
        }
    }
}
