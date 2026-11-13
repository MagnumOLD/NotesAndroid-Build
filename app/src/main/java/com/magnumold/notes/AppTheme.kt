package com.magnumold.notes

import androidx.compose.ui.graphics.Color

internal val defaultNoteColors = listOf(
    Color(0xFF40C8E0), Color(0xFFFF9F0A), Color(0xFFFFD60A),
    Color(0xFF30D158), Color(0xFFFF375F), Color(0xFFBF5AF2)
)
internal data class CustomThemeColors(
    val background: Color = Color(0xFFF2F2F7),
    val topBar: Color = Color(0xFFFFFFFF),
    val statusBar: Color = Color(0xFFFFFFFF),
    val selection: Color = Color(0xFFDCEBFF),
    val addButton: Color = Color(0xFF007AFF),
    val collectionText: Color = Color(0xFF007AFF)
)
internal data class ThemeColorSets(
    val light: CustomThemeColors = CustomThemeColors(
        background = Color.White, topBar = Color.White, statusBar = Color.White,
        selection = Color(0xFFDCEBFF), addButton = Color(0xFF007AFF),
        collectionText = Color(0xFF007AFF)
    ),
    val dark: CustomThemeColors = CustomThemeColors(
        background = Color.Black, topBar = Color.Black, statusBar = Color.Black,
        selection = Color(0xFF1C2B40), addButton = Color(0xFF0A84FF),
        collectionText = Color(0xFF0A84FF)
    ),
    val custom: CustomThemeColors = CustomThemeColors()
)
internal val defaultCustomThemeColors = CustomThemeColors()
internal fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)
internal fun colorToHex(color: Color): String = String.format("#%08X", color.toArgbInt())
internal fun themeColorsForMode(sets: ThemeColorSets, mode: String): CustomThemeColors = when (mode) {
    "light" -> sets.light
    "dark" -> sets.dark
    else -> sets.custom
}
internal fun updateThemeColorSets(sets: ThemeColorSets, mode: String, key: String, color: Color): ThemeColorSets {
    fun update(current: CustomThemeColors) = when (key) {
        "background" -> current.copy(background = color)
        "topBar" -> current.copy(topBar = color)
        "statusBar" -> current.copy(statusBar = color)
        "selection" -> current.copy(selection = color)
        "addButton" -> current.copy(addButton = color)
        else -> current.copy(collectionText = color)
    }
    return when (mode) {
        "light" -> sets.copy(light = update(sets.light))
        "dark" -> sets.copy(dark = update(sets.dark))
        else -> sets.copy(custom = update(sets.custom))
    }
}
internal fun customThemeColor(colors: CustomThemeColors, key: String): Color = when (key) {
    "background" -> colors.background
    "topBar" -> colors.topBar
    "statusBar" -> colors.statusBar
    "selection" -> colors.selection
    "addButton" -> colors.addButton
    else -> colors.collectionText
}
private val colorPickerPresets = listOf(
    Color(0xFF40C8E0), Color(0xFFFF9F0A), Color(0xFFFFD60A), Color(0xFF30D158),
    Color(0xFFFF375F), Color(0xFFBF5AF2), Color(0xFF007AFF), Color(0xFF5E5CE6),
    Color(0xFF8E8E93), Color(0xFFA2845E), Color(0xFFFF6961), Color(0xFF64D2FF)
)
internal fun noteTextColor(color: Color, background: Color = Color.White): Color {
    val alpha = color.alpha
    val visibleRed = color.red * alpha + background.red * (1f - alpha)
    val visibleGreen = color.green * alpha + background.green * (1f - alpha)
    val visibleBlue = color.blue * alpha + background.blue * (1f - alpha)
    val luminance = 0.299f * visibleRed + 0.587f * visibleGreen + 0.114f * visibleBlue
    return if (luminance < 0.48f) Color.White else Color(0xFF202020)
}
