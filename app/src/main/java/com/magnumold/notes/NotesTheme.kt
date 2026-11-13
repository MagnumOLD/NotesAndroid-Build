package com.magnumold.notes

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
internal fun NotesTheme(dark: Boolean, themeColors: CustomThemeColors, customBackground: Boolean, content: @Composable () -> Unit) {
    val foreground = if (customBackground) noteTextColor(themeColors.background) else if (dark) Color.White else Color.Black
    val appBackground = if (customBackground) themeColors.background else if (dark) Color.Black else Color.White
    val outline = if (dark) Color(0xFF38383A) else Color(0xFFD1D1D6)
    val scheme = if (dark) darkColorScheme(
        primary = themeColors.addButton, onPrimary = noteTextColor(themeColors.addButton),
        background = appBackground, onBackground = foreground,
        surface = appBackground, onSurface = foreground,
        surfaceVariant = themeColors.topBar, onSurfaceVariant = Color(0xFF8E8E93), outline = outline
    ) else lightColorScheme(
        primary = themeColors.addButton, onPrimary = noteTextColor(themeColors.addButton),
        background = appBackground, onBackground = foreground,
        surface = appBackground, onSurface = foreground,
        surfaceVariant = themeColors.topBar, onSurfaceVariant = Color(0xFF8E8E93), outline = outline
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
