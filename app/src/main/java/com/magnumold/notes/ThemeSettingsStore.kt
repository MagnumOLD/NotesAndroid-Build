package com.magnumold.notes

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistence boundary for appearance preferences.
 *
 * Existing preference names, keys, JSON shapes, defaults and legacy migration
 * are intentionally preserved for upgrade compatibility.
 */
internal class ThemeSettingsStore(private val preferences: SharedPreferences) {
private fun parseThemeColor(value: String?, fallback: Color): Color = try {
    if (value.isNullOrBlank()) fallback else Color(android.graphics.Color.parseColor(value))
} catch (_: Exception) { fallback }
private fun themeColorsFromJson(json: JSONObject, defaults: CustomThemeColors): CustomThemeColors {
    val topBar = parseThemeColor(json.optString("topBar"), defaults.topBar)
    return CustomThemeColors(
        background = parseThemeColor(json.optString("background"), defaults.background),
        topBar = topBar,
        statusBar = parseThemeColor(json.optString("statusBar"), topBar),
        selection = parseThemeColor(json.optString("selection"), defaults.selection),
        addButton = parseThemeColor(json.optString("addButton"), defaults.addButton),
        collectionText = parseThemeColor(json.optString("collectionText"), defaults.collectionText)
    )
}
fun loadThemeColorSets(): ThemeColorSets {
    val prefs = preferences
    val raw = prefs.getString("theme_colors_v2", null)
    if (!raw.isNullOrBlank()) return try {
        val json = JSONObject(raw)
        val defaults = ThemeColorSets()
        val darkJson = json.optJSONObject("dark") ?: JSONObject()
        val darkColors = themeColorsFromJson(darkJson, defaults.dark)
        // Migrate the former generic dark selection tint to the original chosen note highlight.
        val migratedDark = if (darkColors.selection == Color(0xFF38383A)) {
            darkColors.copy(selection = Color(0xFF1C2B40))
        } else darkColors
        ThemeColorSets(
            themeColorsFromJson(json.optJSONObject("light") ?: JSONObject(), defaults.light),
            migratedDark,
            themeColorsFromJson(json.optJSONObject("custom") ?: JSONObject(), defaultCustomThemeColors)
        )
    } catch (_: Exception) { ThemeColorSets() }
    val legacy = prefs.getString("custom_theme_colors", null)
    val custom = try {
        if (legacy.isNullOrBlank()) defaultCustomThemeColors
        else themeColorsFromJson(JSONObject(legacy), defaultCustomThemeColors)
    } catch (_: Exception) { defaultCustomThemeColors }
    return ThemeColorSets(custom = custom)
}
fun saveThemeColorSets(sets: ThemeColorSets) {
    fun encode(colors: CustomThemeColors) = JSONObject()
        .put("background", colorToHex(colors.background))
        .put("topBar", colorToHex(colors.topBar))
        .put("statusBar", colorToHex(colors.statusBar))
        .put("selection", colorToHex(colors.selection))
        .put("addButton", colorToHex(colors.addButton))
        .put("collectionText", colorToHex(colors.collectionText))
    val json = JSONObject().put("light", encode(sets.light))
        .put("dark", encode(sets.dark)).put("custom", encode(sets.custom))
    preferences.edit()
        .putString("theme_colors_v2", json.toString()).apply()
}

fun loadNoteColors(): List<Color> {
    val raw = preferences.getString("note_colors", null)
        ?: return defaultNoteColors
    return try {
        val array = JSONArray(raw)
        if (array.length() != 6) defaultNoteColors else (0 until 6).map {
            Color(android.graphics.Color.parseColor(array.getString(it)))
        }
    } catch (_: Exception) { defaultNoteColors }
}
fun saveNoteColors(colors: List<Color>) {
    val array = JSONArray()
    colors.forEach { color ->
        val argb = android.graphics.Color.argb(
            (color.alpha * 255).toInt(), (color.red * 255).toInt(),
            (color.green * 255).toInt(), (color.blue * 255).toInt()
        )
        array.put(String.format("#%08X", argb))
    }
    preferences.edit().putString("note_colors", array.toString()).apply()
}
internal data class NoteColorOpacities(val light: Float = 0.5f, val dark: Float = 0.5f, val custom: Float = 0.5f)

fun loadNoteColorOpacities(): NoteColorOpacities {
    val raw = preferences
        .getString("note_color_opacity_v1", null) ?: return NoteColorOpacities()
    return try {
        val json = JSONObject(raw)
        NoteColorOpacities(
            light = json.optDouble("light", 0.5).toFloat().coerceIn(0f, 1f),
            dark = json.optDouble("dark", 0.5).toFloat().coerceIn(0f, 1f),
            custom = json.optDouble("custom", 0.5).toFloat().coerceIn(0f, 1f)
        )
    } catch (_: Exception) { NoteColorOpacities() }
}

fun saveNoteColorOpacities(values: NoteColorOpacities) {
    val json = JSONObject().put("light", values.light).put("dark", values.dark).put("custom", values.custom)
    preferences.edit()
        .putString("note_color_opacity_v1", json.toString()).apply()
}


}
