package com.magnumold.notes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject







/**
 * Единая шкала отступов интерфейса.
 * Геометрия интерактивных элементов и системные touch-targets задаются отдельно.
 */
/** Shared geometry for every primary circular add button. */
internal object PrimaryAddButtonDimensions {
    val diameter = 56.dp
    val verticalOffset = (-65).dp
}

internal object AppSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp

    val screenHorizontal = lg
    val screenVertical = md
    val toolbarControlInset = xxs
    // Material3 ListItem has a 16.dp default content inset; 4.dp outside
    // aligns its visible content with the app's 20.dp page guide.
    val settingsListItemOuterInset = xxs
}



/**
 * Shared main-list card dimensions.
 * contentHeight is a minimum; long first lines can increase the measured row height.
 */
internal object CardDimensions {
    val contentHeight = 88.dp
    val dividerThickness = 1.dp
    val rowHeight = contentHeight + dividerThickness

    // Align the visible handle with the first 22dp title line.
    // Keep a 40dp touch target centered on that visual position.
    val headerFirstLineHeight = 22.dp
    val handleTouchTargetSize = 40.dp
    val handleHitTargetTopOffset =
        AppSpacing.md + (headerFirstLineHeight - handleTouchTargetSize) / 2f
    // Keep the 40dp hit target centered on the handle, whose right edge now
    // aligns directly with the card metadata/date guide.
    val handleHitTargetRightInset = AppSpacing.screenHorizontal - 12.dp
    // Nested glyphs end at the row edge; center the 40.dp hit target on the 16.dp glyph.
    val nestedHandleHitTargetRightOverhang = 12.dp
    val nestedGroupMinHeight = 72.dp
}


internal fun formatNoteCreationDate(timestamp: Long): String {
    val locale = java.util.Locale("ru")
    val date = java.util.Date(timestamp)
    val target = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = java.util.Calendar.getInstance()
    val sameDay = target.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
        target.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
    if (sameDay) return "Сегодня"

    val yesterday = (today.clone() as java.util.Calendar).apply {
        add(java.util.Calendar.DAY_OF_YEAR, -1)
    }
    val isYesterday = target.get(java.util.Calendar.YEAR) == yesterday.get(java.util.Calendar.YEAR) &&
        target.get(java.util.Calendar.DAY_OF_YEAR) == yesterday.get(java.util.Calendar.DAY_OF_YEAR)
    if (isYesterday) return "Вчера"

    val pattern = if (target.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR)) {
        "EEE. dd.MM"
    } else {
        "EEE. dd.MM.yyyy"
    }
    return java.text.SimpleDateFormat(pattern, locale).format(date)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

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


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialDark = getPreferences(Context.MODE_PRIVATE).getBoolean("dark_v2", true)
        val savedThemeMode = getPreferences(Context.MODE_PRIVATE).getString("theme_mode", null)
            ?.takeIf { it in listOf("light", "dark", "custom") } ?: if (initialDark) "dark" else "light"
        val initialThemeColorSets = loadThemeColorSets(this)
        val initialThemeColors = themeColorsForMode(initialThemeColorSets, savedThemeMode)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(
            if (savedThemeMode == "custom") initialThemeColors.background.toArgbInt()
            else if (savedThemeMode == "dark") android.graphics.Color.BLACK else android.graphics.Color.WHITE
        ))
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val repository = NotesRepository(getPreferences(Context.MODE_PRIVATE))
        val recordsViewModel = ViewModelProvider(this, NotesViewModelFactory(repository))[NotesViewModel::class.java]
        setContent {
            NotesApp(
                activity = this@MainActivity,
                recordsViewModel = recordsViewModel,
                savedThemeMode = savedThemeMode,
                initialThemeColorSets = initialThemeColorSets
            )
        }
    }

    internal fun shareNote(note: Note) {
        val text = note.blocks.joinToString("\n\n") { (if (it.isChecklist) (if (it.checked) "☑ " else "☐ ") else "") + it.text }
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        startActivity(Intent.createChooser(intent, "Поделиться заметкой"))
    }

}