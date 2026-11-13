package com.magnumold.notes

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

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
private fun loadThemeColorSets(context: Context): ThemeColorSets {
    val prefs = context.getSharedPreferences("notes_settings", Context.MODE_PRIVATE)
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
internal fun saveThemeColorSets(context: Context, sets: ThemeColorSets) {
    fun encode(colors: CustomThemeColors) = JSONObject()
        .put("background", colorToHex(colors.background))
        .put("topBar", colorToHex(colors.topBar))
        .put("statusBar", colorToHex(colors.statusBar))
        .put("selection", colorToHex(colors.selection))
        .put("addButton", colorToHex(colors.addButton))
        .put("collectionText", colorToHex(colors.collectionText))
    val json = JSONObject().put("light", encode(sets.light))
        .put("dark", encode(sets.dark)).put("custom", encode(sets.custom))
    context.getSharedPreferences("notes_settings", Context.MODE_PRIVATE).edit()
        .putString("theme_colors_v2", json.toString()).apply()
}
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
internal fun loadNoteColors(context: Context): List<Color> {
    val raw = context.getSharedPreferences("notes_settings", Context.MODE_PRIVATE).getString("note_colors", null)
        ?: return defaultNoteColors
    return try {
        val array = JSONArray(raw)
        if (array.length() != 6) defaultNoteColors else (0 until 6).map {
            Color(android.graphics.Color.parseColor(array.getString(it)))
        }
    } catch (_: Exception) { defaultNoteColors }
}
internal fun saveNoteColors(context: Context, colors: List<Color>) {
    val array = JSONArray()
    colors.forEach { color ->
        val argb = android.graphics.Color.argb(
            (color.alpha * 255).toInt(), (color.red * 255).toInt(),
            (color.green * 255).toInt(), (color.blue * 255).toInt()
        )
        array.put(String.format("#%08X", argb))
    }
    context.getSharedPreferences("notes_settings", Context.MODE_PRIVATE).edit().putString("note_colors", array.toString()).apply()
}
internal data class NoteColorOpacities(val light: Float = 0.5f, val dark: Float = 0.5f, val custom: Float = 0.5f)

internal fun loadNoteColorOpacities(context: Context): NoteColorOpacities {
    val raw = context.getSharedPreferences("notes_settings", Context.MODE_PRIVATE)
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

internal fun saveNoteColorOpacities(context: Context, values: NoteColorOpacities) {
    val json = JSONObject().put("light", values.light).put("dark", values.dark).put("custom", values.custom)
    context.getSharedPreferences("notes_settings", Context.MODE_PRIVATE).edit()
        .putString("note_color_opacity_v1", json.toString()).apply()
}

internal fun noteTextColor(color: Color, background: Color = Color.White): Color {
    val alpha = color.alpha
    val visibleRed = color.red * alpha + background.red * (1f - alpha)
    val visibleGreen = color.green * alpha + background.green * (1f - alpha)
    val visibleBlue = color.blue * alpha + background.blue * (1f - alpha)
    val luminance = 0.299f * visibleRed + 0.587f * visibleGreen + 0.114f * visibleBlue
    return if (luminance < 0.48f) Color.White else Color(0xFF202020)
}
