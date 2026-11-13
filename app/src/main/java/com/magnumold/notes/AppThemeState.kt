package com.magnumold.notes

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Owns the mutable appearance state used by the root app composition.
 * Persistence remains in ThemeSettingsStore; this class contains only state
 * transitions and derived values for the active theme.
 */
@Stable
internal class AppThemeState(
    initialMode: String,
    initialColorSets: ThemeColorSets,
    initialNoteColors: List<Color>,
    initialNoteColorOpacities: NoteColorOpacities
) {
    var mode by mutableStateOf(initialMode)
        private set

    var colorSets by mutableStateOf(initialColorSets)
        private set

    var noteColors by mutableStateOf(initialNoteColors)
        private set

    var noteColorOpacities by mutableStateOf(initialNoteColorOpacities)
        private set

    val activeColors: CustomThemeColors
        get() = themeColorsForMode(colorSets, mode)

    val isDark: Boolean
        get() = when (mode) {
            "dark" -> true
            "custom" -> noteTextColor(activeColors.background) == Color.White
            else -> false
        }

    val activeNoteColorOpacity: Float
        get() = when (mode) {
            "light" -> noteColorOpacities.light
            "dark" -> noteColorOpacities.dark
            else -> noteColorOpacities.custom
        }

    fun setMode(value: String) {
        if (value in listOf("light", "dark", "custom")) mode = value
    }

    fun updateThemeColor(key: String, color: Color) {
        colorSets = updateThemeColorSets(colorSets, mode, key, color)
    }

    fun resetThemeColors() {
        val defaults = when (mode) {
            "light" -> ThemeColorSets().light
            "dark" -> ThemeColorSets().dark
            else -> defaultCustomThemeColors
        }
        colorSets = when (mode) {
            "light" -> colorSets.copy(light = defaults)
            "dark" -> colorSets.copy(dark = defaults)
            else -> colorSets.copy(custom = defaults)
        }
    }

    fun setNoteColors(colors: List<Color>) {
        noteColors = colors
    }

    fun setNoteColorOpacity(opacity: Float) {
        val value = opacity.coerceIn(0f, 1f)
        noteColorOpacities = when (mode) {
            "light" -> noteColorOpacities.copy(light = value)
            "dark" -> noteColorOpacities.copy(dark = value)
            else -> noteColorOpacities.copy(custom = value)
        }
    }
}
