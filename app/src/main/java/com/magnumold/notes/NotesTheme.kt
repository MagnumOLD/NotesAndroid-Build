package com.magnumold.notes

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * One app-wide Material typography scale.
 *
 * FontFamily.Default deliberately keeps the native Android sans-serif rather
 * than introducing a bundled font that could change the approved NotesHome
 * appearance. Explicit sizes, line heights and weights prevent Material3
 * defaults from drifting between screens and components.
 */
internal val AppMaterialTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Light),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Light),
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

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
    MaterialTheme(
        colorScheme = scheme,
        typography = AppMaterialTypography,
        content = content
    )
}
