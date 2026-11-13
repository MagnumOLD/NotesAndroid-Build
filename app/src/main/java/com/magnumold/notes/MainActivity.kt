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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch

import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation



import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

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
private fun Color.toArgbInt(): Int = android.graphics.Color.argb(
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
private fun saveThemeColorSets(context: Context, sets: ThemeColorSets) {
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
private fun themeColorsForMode(sets: ThemeColorSets, mode: String): CustomThemeColors = when (mode) {
    "light" -> sets.light
    "dark" -> sets.dark
    else -> sets.custom
}
private fun updateThemeColorSets(sets: ThemeColorSets, mode: String, key: String, color: Color): ThemeColorSets {
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
private fun loadNoteColors(context: Context): List<Color> {
    val raw = context.getSharedPreferences("notes_settings", Context.MODE_PRIVATE).getString("note_colors", null)
        ?: return defaultNoteColors
    return try {
        val array = JSONArray(raw)
        if (array.length() != 6) defaultNoteColors else (0 until 6).map {
            Color(android.graphics.Color.parseColor(array.getString(it)))
        }
    } catch (_: Exception) { defaultNoteColors }
}
private fun saveNoteColors(context: Context, colors: List<Color>) {
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
private data class NoteColorOpacities(val light: Float = 0.5f, val dark: Float = 0.5f, val custom: Float = 0.5f)

private fun loadNoteColorOpacities(context: Context): NoteColorOpacities {
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

private fun saveNoteColorOpacities(context: Context, values: NoteColorOpacities) {
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
            val snapshot by recordsViewModel.snapshot.collectAsStateWithLifecycle()
            var themeMode by remember { mutableStateOf(savedThemeMode) }
            var themeColorSets by remember { mutableStateOf(initialThemeColorSets) }
            val activeThemeColors = themeColorsForMode(themeColorSets, themeMode)
            val dark = when (themeMode) {
                "dark" -> true
                "custom" -> noteTextColor(activeThemeColors.background) == Color.White
                else -> false
            }
            var noteColors by remember { mutableStateOf(loadNoteColors(this@MainActivity)) }
            var noteColorOpacities by remember { mutableStateOf(loadNoteColorOpacities(this@MainActivity)) }
            val activeNoteColorOpacity = when (themeMode) {
                "light" -> noteColorOpacities.light
                "dark" -> noteColorOpacities.dark
                else -> noteColorOpacities.custom
            }
            val displayedNoteColors = remember(noteColors, activeNoteColorOpacity) {
                noteColors.map { it.copy(alpha = activeNoteColorOpacity) }
            }
            val notes = snapshot.notes
            val collections = snapshot.collections
            var activeCollectionId by remember { mutableStateOf<Long?>(null) }
            var selectedId by remember { mutableStateOf<Long?>(null) }
            val activeNotes = if (activeCollectionId == null) notes.toList()
                else collections.firstOrNull { it.id == activeCollectionId }?.notes.orEmpty()
            fun updateActiveNotes(updated: List<Note>) {
                val collectionId = activeCollectionId
                recordsViewModel.updateSnapshot { current ->
                    if (collectionId == null) current.copy(notes = updated)
                    else current.copy(collections = current.collections.map { collection ->
                        if (collection.id == collectionId) collection.copy(notes = updated) else collection
                    })
                }
            }
            var autoFocusNewNote by remember { mutableStateOf(false) }
            var search by remember { mutableStateOf("") }
            var searchVisible by remember { mutableStateOf(false) }
            var settingsVisible by remember { mutableStateOf(false) }
            var vaultScreen by remember { mutableStateOf<String?>(null) }
            val archived = snapshot.archived
            val trash = snapshot.trash

            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                if (uri != null) {
                    try {
                        contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(createBackupJson(snapshot.notes, snapshot.collections, snapshot.archived, snapshot.trash).toByteArray(Charsets.UTF_8))
                        } ?: error("Не удалось открыть файл")
                        Toast.makeText(this, "Резервная копия сохранена", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(this, "Не удалось сохранить резервную копию", Toast.LENGTH_LONG).show()
                    }
                }
            }
            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    try {
                        val json = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                            ?: error("Не удалось прочитать файл")
                        val importedCount = recordsViewModel.importBackup(json)
                        Toast.makeText(this, "Импортировано заметок: $importedCount", Toast.LENGTH_LONG).show()
                    } catch (_: Exception) {
                        Toast.makeText(this, "Не удалось импортировать файл: неверный формат", Toast.LENGTH_LONG).show()
                    }
                }
            }

            BackHandler(enabled = settingsVisible && vaultScreen != null) { vaultScreen = null }
            BackHandler(enabled = settingsVisible && vaultScreen == null) { settingsVisible = false }

            SideEffect {
                val appBackground = if (themeMode == "custom") activeThemeColors.background else if (dark) Color.Black else Color.White
                val statusBarBackground = activeThemeColors.statusBar
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(appBackground.toArgbInt()))
                window.statusBarColor = statusBarBackground.toArgbInt()
                val flags = window.decorView.systemUiVisibility
                val darkStatusIcons = noteTextColor(statusBarBackground) == Color(0xFF202020)
                window.decorView.systemUiVisibility = if (darkStatusIcons) {
                    flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else {
                    flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
            }
            LaunchedEffect(themeMode, dark) {
                getPreferences(Context.MODE_PRIVATE).edit().putString("theme_mode", themeMode)
                    .putBoolean("dark_v2", dark).apply()
            }
            LaunchedEffect(themeColorSets) { saveThemeColorSets(this@MainActivity, themeColorSets) }
            LaunchedEffect(noteColors) { saveNoteColors(this@MainActivity, noteColors) }
            LaunchedEffect(noteColorOpacities) { saveNoteColorOpacities(this@MainActivity, noteColorOpacities) }

            NotesTheme(dark, activeThemeColors, themeMode == "custom") {
                val selected = activeNotes.firstOrNull { it.id == selectedId }
                if (settingsVisible && selected == null && vaultScreen != null) {
                    VaultScreen(
                        title = if (vaultScreen == "archive") "Архив" else "Корзина",
                        entries = if (vaultScreen == "archive") archived else trash,
                        isTrash = vaultScreen == "trash",
                        onBack = { vaultScreen = null },
                        onRestore = { entry -> recordsViewModel.restoreVaultEntry(entry, fromArchive = vaultScreen == "archive") },
                        onMoveToTrash = { entry -> recordsViewModel.moveArchivedToTrash(entry) },
                        onDeleteForever = { entry -> recordsViewModel.deleteFromTrash(entry) },
                        onEmptyTrash = { recordsViewModel.emptyTrash() },
                        themeColors = activeThemeColors
                    )
                } else if (settingsVisible && selected == null) {
                    SettingsScreen(
                        themeMode = themeMode,
                        onThemeModeChange = { themeMode = it },
                        themeColors = activeThemeColors,
                        onThemeColorChange = { key, color -> themeColorSets = updateThemeColorSets(themeColorSets, themeMode, key, color) },
                        onResetThemeColors = {
                            val defaults = when (themeMode) { "light" -> ThemeColorSets().light; "dark" -> ThemeColorSets().dark; else -> defaultCustomThemeColors }
                            themeColorSets = when (themeMode) {
                                "light" -> themeColorSets.copy(light = defaults)
                                "dark" -> themeColorSets.copy(dark = defaults)
                                else -> themeColorSets.copy(custom = defaults)
                            }
                        },
                        noteColors = noteColors,
                        noteColorOpacity = activeNoteColorOpacity,
                        onNoteColorsChange = { noteColors = it },
                        onNoteColorOpacityChange = { opacity ->
                            noteColorOpacities = when (themeMode) {
                                "light" -> noteColorOpacities.copy(light = opacity)
                                "dark" -> noteColorOpacities.copy(dark = opacity)
                                else -> noteColorOpacities.copy(custom = opacity)
                            }
                        },
                        onExport = { exportLauncher.launch("Notes-backup.json") },
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
                        onOpenArchive = { vaultScreen = "archive" },
                        onOpenTrash = { vaultScreen = "trash" },
                        onBack = { settingsVisible = false }
                    )
                } else if (selected == null) {
                    NotesHome(
                        notes = activeNotes, search = search, searchVisible = searchVisible,
                        noteColors = displayedNoteColors,
                        themeColors = activeThemeColors,
                        collections = collections, activeCollectionId = activeCollectionId,
                        onSelectCollection = { activeCollectionId = it; selectedId = null },
                        onCreateCollection = { name -> val id = newRecordId(); recordsViewModel.updateSnapshot { it.copy(collections = it.collections + NoteCollection(id, name)) }; activeCollectionId = id },
                        onRenameCollection = { id, name ->
                            recordsViewModel.updateSnapshot { current -> current.copy(collections = current.collections.map { if (it.id == id) it.copy(name = name) else it }) }
                        },
                        onDeleteCollection = { id ->
                            val collection = collections.firstOrNull { it.id == id }
                            if (collection != null) { recordsViewModel.updateSnapshot { current -> current.copy(notes = collection.notes + current.notes, collections = current.collections.filterNot { it.id == id }) }; if (activeCollectionId == id) activeCollectionId = null }
                        },
                        onMoveCollection = { id, delta ->
                            recordsViewModel.updateSnapshot { current ->
                                val from = current.collections.indexOfFirst { it.id == id }
                                if (from < 0 || current.collections.isEmpty()) current else {
                                    val to = (from + delta).coerceIn(0, current.collections.lastIndex)
                                    if (from == to) current else current.copy(collections = current.collections.toMutableList().apply { add(to, removeAt(from)) })
                                }
                            }
                        },
                        onTransferSelected = { ids, targetId ->
                            if (targetId != activeCollectionId) {
                                val moving = activeNotes.filter { it.id in ids }
                                updateActiveNotes(activeNotes.filterNot { it.id in ids })
                                recordsViewModel.updateSnapshot { current ->
                                    if (targetId == null) current.copy(notes = moving + current.notes)
                                    else current.copy(collections = current.collections.map { collection ->
                                        if (collection.id == targetId) collection.copy(notes = moving + collection.notes) else collection
                                    })
                                }
                            }
                        },
                        onSearch = { search = it },
                        onToggleSearch = { searchVisible = !searchVisible; if (!searchVisible) search = "" },
                        dark = dark,
                        onOpenSettings = { settingsVisible = true },
                        onOpen = { autoFocusNewNote = false; selectedId = it },
                        onArchiveSelected = { ids ->
                            recordsViewModel.moveActiveRecordsToVault(activeCollectionId, ids, toTrash = false)
                        },
                        onDeleteSelected = { ids ->
                            recordsViewModel.moveActiveRecordsToVault(activeCollectionId, ids, toTrash = true)
                        },
                        onMoveSelected = { ids, afterId ->
                            val reordered = NoteReorderModel.moveSelected(activeNotes, ids, afterId)
                            if (reordered != activeNotes) updateActiveNotes(reordered)
                        },
                        onTogglePinned = { ids ->
                            val current = activeNotes.toMutableList()
                            val allPinned = current.filter { it.id in ids }.all { it.pinned }
                            val updated = current.map { if (it.id in ids) it.copy(pinned = !allPinned) else it }
                            updateActiveNotes(updated.sortedByDescending { it.pinned })
                        },
                        onChangeNoteColor = { noteId, colorIndex ->
                            val index = activeNotes.indexOfFirst { it.id == noteId }
                            if (index >= 0) updateActiveNotes(activeNotes.toMutableList().apply { this[index] = this[index].copy(colorIndex = colorIndex) })
                        },
                        onCreate = { type ->
                            val now = System.currentTimeMillis()
                            val id = newRecordId()
                            updateActiveNotes(listOf(Note(id, "", listOf(NoteBlock()), now, created = now, type = type)) + activeNotes)
                            autoFocusNewNote = true
                            selectedId = id
                        }
                    )
                } else {
                    RecordEditor(
                        note = selected,
                        dark = dark,
                        topBarColor = activeThemeColors.topBar,
                        addButtonColor = activeThemeColors.addButton,
                        selectionColor = activeThemeColors.selection,
                        autoFocus = autoFocusNewNote,
                        onBack = {
                            if (!selected.hasContent()) updateActiveNotes(activeNotes.filterNot { it.id == selected.id })
                            selectedId = null
                            autoFocusNewNote = false
                        },
                        onChange = { changed ->
                            val index = activeNotes.indexOfFirst { it.id == changed.id }
                            if (index >= 0) updateActiveNotes(activeNotes.toMutableList().apply { this[index] = changed.copy(updated = System.currentTimeMillis()) })
                        },
                        onDelete = { promoteChildren ->
                            recordsViewModel.moveActiveRecordsToVault(activeCollectionId, setOf(selected.id), toTrash = true)
                            selectedId = null
                            autoFocusNewNote = false
                        },
                        onShare = { shareNote(selected) }
                    )
                }
            }
        }
    }

    private fun shareNote(note: Note) {
        val text = note.blocks.joinToString("\n\n") { (if (it.isChecklist) (if (it.checked) "☑ " else "☐ ") else "") + it.text }
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        startActivity(Intent.createChooser(intent, "Поделиться заметкой"))
    }


}

@Composable
private fun NotesTheme(dark: Boolean, themeColors: CustomThemeColors, customBackground: Boolean, content: @Composable () -> Unit) {
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultScreen(
    title: String,
    entries: List<VaultNote>,
    isTrash: Boolean,
    onBack: () -> Unit,
    onRestore: (VaultNote) -> Unit,
    onMoveToTrash: (VaultNote) -> Unit,
    onDeleteForever: (VaultNote) -> Unit,
    onEmptyTrash: () -> Unit,
    themeColors: CustomThemeColors
) {
    var confirmEmpty by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = themeColors.topBar, contentColor = noteTextColor(themeColors.topBar)) {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(64.dp).padding(horizontal = AppSpacing.toolbarControlInset),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { AppIcon(NoteIcon.Back, "Назад") }
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (isTrash && entries.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text("Очистить") }
                }
            }
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(if (isTrash) "Корзина пуста" else "Архив пуст", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = AppSpacing.xs)) {
                items(entries, key = { it.note.id }) { entry ->
                    val preview = entry.note.blocks.asSequence().mapNotNull { block ->
                        block.text.lineSequence().firstOrNull { it.isNotBlank() }
                    }.firstOrNull() ?: "Пустая заметка"
                    Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.xs)) {
                        Text(preview, fontSize = 16.sp, maxLines = 2)
                        Text(if (entry.collectionId == null) "Основной блокнот" else "Подборка",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = AppSpacing.xxs))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (!isTrash) TextButton(onClick = { onMoveToTrash(entry) }) { Text("Удалить") }
                            if (isTrash) TextButton(onClick = { onDeleteForever(entry) }) { Text("Удалить навсегда") }
                            TextButton(onClick = { onRestore(entry) }) { Text("Восстановить") }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
            }
        }
    }
    if (confirmEmpty) AlertDialog(
        onDismissRequest = { confirmEmpty = false },
        title = { Text("Очистить корзину?") },
        text = { Text("Все заметки будут удалены без возможности восстановления.") },
        confirmButton = { TextButton(onClick = { onEmptyTrash(); confirmEmpty = false }) { Text("Очистить") } },
        dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Отмена") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotesHome(
    notes: List<Note>, search: String, searchVisible: Boolean, noteColors: List<Color>,
    themeColors: CustomThemeColors, onSearch: (String) -> Unit,
    onToggleSearch: () -> Unit, dark: Boolean, onOpenSettings: () -> Unit,
    onOpen: (Long) -> Unit, onCreate: (RecordType) -> Unit,
    onChangeNoteColor: (Long, Int) -> Unit, onDeleteSelected: (Set<Long>) -> Unit, onArchiveSelected: (Set<Long>) -> Unit,
    onTogglePinned: (Set<Long>) -> Unit,
    onMoveSelected: (Set<Long>, Long?) -> Unit,
    collections: List<NoteCollection>, activeCollectionId: Long?,
    onSelectCollection: (Long?) -> Unit, onCreateCollection: (String) -> Unit,
    onRenameCollection: (Long, String) -> Unit, onDeleteCollection: (Long) -> Unit,
    onMoveCollection: (Long, Int) -> Unit, onTransferSelected: (Set<Long>, Long?) -> Unit
) {
    var selectedNoteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showMoveMenu by remember { mutableStateOf(false) }
    var collectionMenuId by remember { mutableStateOf<Long?>(null) }
    var showNewCollectionDialog by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }
    var renameCollectionId by remember { mutableStateOf<Long?>(null) }
    var renameCollectionName by remember { mutableStateOf("") }
    var deleteCollectionId by remember { mutableStateOf<Long?>(null) }
    var createMenuExpanded by remember { mutableStateOf(false) }
    var deadlineClock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            deadlineClock = System.currentTimeMillis()
        }
    }

    var draggingIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var dragBaseRows by remember { mutableStateOf<Map<String, DragRow>>(emptyMap()) }
    var reorderSession by remember { mutableStateOf<ReorderSession?>(null) }
    var dragPointerY by remember { mutableFloatStateOf(0f) }
    var dragGrabOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragReleaseRequested by remember { mutableStateOf(false) }
    val measuredNoteCardHeights = remember { mutableStateMapOf<Long, Int>() }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val orderedNotes = notes.sortedByDescending { it.pinned }
    val filtered = orderedNotes.filter { it.matchesSearch(search) }
    val selectionMode = selectedNoteIds.isNotEmpty()
    val homeBarColor = themeColors.topBar
    val homeContentColor = noteTextColor(homeBarColor)
    val selectedNoteBackground = themeColors.selection
    val isDragging = draggingIds.isNotEmpty()
    val dragRows = reorderSession?.keys?.mapNotNull { dragBaseRows[it] }.orEmpty()
    val visibleDragRows = if (isDragging && search.isBlank()) dragRows
        else filtered.map { DragRow(listOf(it), it.id.toString()) }

    fun finishDrag() {
        val ids = draggingIds
        if (ids.isNotEmpty()) {
            onMoveSelected(ids, NoteReorderModel.afterId(dragRows, ids))
        }
        draggingIds = emptySet()
        reorderSession = null
        dragBaseRows = emptyMap()
        dragPointerY = 0f
        dragGrabOffsetPx = 0f
        dragReleaseRequested = false
    }

    fun startDrag(note: Note, rootY: Float, grabOffsetInRow: Float) {
        val ids = if (note.id in selectedNoteIds && selectedNoteIds.size > 1) {
            selectedNoteIds
        } else {
            setOf(note.id)
        }
        val rows = NoteReorderModel.createDragRows(orderedNotes, ids, note.id) ?: return

        dragBaseRows = rows.associateBy { it.key }
        reorderSession = AdjacentReorderEngine.begin(rows.map { it.key }, note.id.toString())
        draggingIds = ids
        dragPointerY = rootY
        dragGrabOffsetPx = grabOffsetInRow
        dragReleaseRequested = false
    }

    // Keep gesture tracking on the stable viewport, not on a row that LazyColumn repositions.
    val latestIsDraggingForPointer = rememberUpdatedState(isDragging)
    val latestSearchBlankForPointer = rememberUpdatedState(search.isBlank())
    val latestRowsForPointer = rememberUpdatedState(visibleDragRows)
    val latestStartDrag = rememberUpdatedState<(Note, Float, Float) -> Unit> { note, pointerY, grabOffset ->
        startDrag(note, pointerY, grabOffset)
    }

    AdjacentReorderEffect(
        isDragging = isDragging,
        orderedKeys = dragRows.map { it.key },
        reorderSession = reorderSession,
        pointerY = dragPointerY,
        grabOffsetPx = dragGrabOffsetPx,
        releaseRequested = dragReleaseRequested,
        listState = listState,
        density = density,
        canSwap = { movingKey, neighbourKey ->
            val movingPinStates = dragBaseRows[movingKey]?.notes?.map { it.pinned }?.distinct().orEmpty()
            val neighbourPinStates = dragBaseRows[neighbourKey]?.notes?.map { it.pinned }?.distinct().orEmpty()
            movingPinStates.size == 1 && neighbourPinStates.size == 1 &&
                movingPinStates.single() == neighbourPinStates.single()
        },
        onSessionChange = { reorderSession = it },
        onFinishDrag = { finishDrag() }
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = homeBarColor, contentColor = homeContentColor) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .height(64.dp)
                        .padding(start = AppSpacing.screenHorizontal, end = AppSpacing.toolbarControlInset),
                    verticalAlignment = Alignment.CenterVertically                ) {
                    if (selectionMode) {
                        Text("${selectedNoteIds.size} выбрано", fontWeight = FontWeight.Medium, fontSize = 18.sp)
                        Spacer(Modifier.weight(1f))
                        val allSelectedPinned = notes.filter { it.id in selectedNoteIds }.all { it.pinned }
                        IconButton(onClick = { onTogglePinned(selectedNoteIds) }) {
                            AppIcon(NoteIcon.Pin, contentDescription = if (allSelectedPinned) "Открепить заметки" else "Закрепить заметки")
                        }
                        IconButton(onClick = { onArchiveSelected(selectedNoteIds); selectedNoteIds = emptySet() }) { AppIcon(NoteIcon.Archive, contentDescription = "В архив") }
                        IconButton(onClick = { showMoveMenu = true }) { AppIcon(NoteIcon.Folder, contentDescription = "Переместить заметки") }
                        DropdownMenu(expanded = showMoveMenu, onDismissRequest = { showMoveMenu = false }) {
                            if (activeCollectionId != null) DropdownMenuItem(text = { Text("В основной блокнот") }, onClick = {
                                onTransferSelected(selectedNoteIds, null); selectedNoteIds = emptySet(); showMoveMenu = false
                            })
                            collections.filter { it.id != activeCollectionId }.forEach { collection ->
                                DropdownMenuItem(text = { Text(collection.name) }, onClick = {
                                    onTransferSelected(selectedNoteIds, collection.id); selectedNoteIds = emptySet(); showMoveMenu = false
                                })
                            }
                            if (collections.isEmpty() || (collections.size == 1 && activeCollectionId != null)) {
                                DropdownMenuItem(text = { Text("Нет других подборок") }, enabled = false, onClick = {})
                            }
                        }
                        IconButton(onClick = { selectedNoteIds = emptySet() }) {
                            AppIcon(NoteIcon.Close, contentDescription = "Отменить выделение")
                        }
                        IconButton(onClick = { showDeleteConfirmation = true }) { AppIcon(NoteIcon.Trash, contentDescription = "Удалить выбранные заметки") }
                    } else {
                        Text("Заметки", fontWeight = FontWeight.Medium, fontSize = 18.sp)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onToggleSearch) {
                            AppIcon(if (searchVisible) NoteIcon.Close else NoteIcon.Search, contentDescription = "Поиск")
                        }
                        IconButton(onClick = onOpenSettings) {
                            AppIcon(NoteIcon.More, contentDescription = "Настройки")
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!selectionMode && !isDragging) {
                Box {
                    Box(
                        modifier = Modifier
                            .offset(y = PrimaryAddButtonDimensions.verticalOffset)
                            .size(PrimaryAddButtonDimensions.diameter)
                            .shadow(6.dp, androidx.compose.foundation.shape.CircleShape)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(themeColors.addButton)
                            .combinedClickable(
                                role = Role.Button,
                                onClick = { onCreate(RecordType.NOTE) },
                                onLongClick = { createMenuExpanded = true }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "+",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Light,
                            color = noteTextColor(themeColors.addButton)
                        )
                    }
                    DropdownMenu(
                        expanded = createMenuExpanded,
                        onDismissRequest = { createMenuExpanded = false }
                    ) {
                        DropdownMenuItem(text = { Text("Задача") }, onClick = {
                            createMenuExpanded = false
                            onCreate(RecordType.TASK)
                        })
                        DropdownMenuItem(text = { Text("Покупка") }, onClick = {
                            createMenuExpanded = false
                            onCreate(RecordType.PURCHASE)
                        })
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().height(48.dp).padding(start = AppSpacing.screenHorizontal, end = AppSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                    Text("Все",
                        color = if (activeCollectionId == null) themeColors.collectionText else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp, fontWeight = if (activeCollectionId == null) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.pointerInput(activeCollectionId) { detectTapGestures(onTap = { onSelectCollection(null); selectedNoteIds = emptySet() }) })
                    collections.forEach { collection ->
                        Box {
                            Text(collection.name,
                                color = if (activeCollectionId == collection.id) themeColors.collectionText else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp, fontWeight = if (activeCollectionId == collection.id) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                modifier = Modifier.pointerInput(collection.id, activeCollectionId) {
                                    detectTapGestures(onTap = { onSelectCollection(collection.id); selectedNoteIds = emptySet() },
                                        onLongPress = { collectionMenuId = collection.id })
                                })
                            DropdownMenu(expanded = collectionMenuId == collection.id, onDismissRequest = { collectionMenuId = null }) {
                                DropdownMenuItem(text = { Text("Переименовать") }, onClick = {
                                    renameCollectionId = collection.id; renameCollectionName = collection.name; collectionMenuId = null
                                })
                                DropdownMenuItem(text = { Text("Переместить влево") }, enabled = collections.indexOfFirst { it.id == collection.id } > 0, onClick = {
                                    onMoveCollection(collection.id, -1); collectionMenuId = null
                                })
                                DropdownMenuItem(text = { Text("Переместить вправо") }, enabled = collections.indexOfFirst { it.id == collection.id } < collections.lastIndex, onClick = {
                                    onMoveCollection(collection.id, 1); collectionMenuId = null
                                })
                                DropdownMenuItem(text = { Text("Удалить подборку") }, onClick = {
                                    deleteCollectionId = collection.id; collectionMenuId = null
                                })
                            }
                        }
                    }
                }
                IconButton(onClick = { newCollectionName = ""; showNewCollectionDialog = true }) {
                    AppIcon(NoteIcon.Add, contentDescription = "Создать подборку", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (searchVisible) OutlinedTextField(value = search, onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.sm),
                placeholder = { Text("Поиск по заметкам") }, singleLine = true)

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (search.isNotBlank()) "Ничего не найдено" else "Пока нет заметок")
                }
            } else {
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(selectionMode) {
                            if (selectionMode) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    // Handle touch target: 40dp wide with a 28dp right inset.
                                    val handleZone = 72.dp.toPx()
                                    if (down.position.x < size.width - handleZone) {
                                        var totalX = 0f
                                        var totalY = 0f
                                        var finished = false
                                        val clearDistance = 64.dp.toPx()
                                        while (!finished) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull() ?: break
                                            if (!change.pressed) {
                                                finished = true
                                            } else {
                                                val delta = change.position - change.previousPosition
                                                totalX += delta.x
                                                totalY += delta.y
                                                val absX = kotlin.math.abs(totalX)
                                                val absY = kotlin.math.abs(totalY)
                                                val touchSlop = viewConfiguration.touchSlop
                                                if (absY > touchSlop && absY > absX) {
                                                    finished = true
                                                } else if (absX >= clearDistance && absX > absY * 1.25f) {
                                                    selectedNoteIds = emptySet()
                                                    change.consume()
                                                    finished = true
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial
                                )
                                if (latestIsDraggingForPointer.value ||
                                    !latestSearchBlankForPointer.value
                                ) return@awaitEachGesture

                                // Hit-test the handle in viewport coordinates. This listener
                                // remains attached while the keyed row changes its list position.
                                val handleRight = size.width - CardDimensions.handleHitTargetRightInset.toPx()
                                val handleLeft = handleRight - 40.dp.toPx()
                                if (down.position.x < handleLeft ||
                                    down.position.x > handleRight
                                ) return@awaitEachGesture

                                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                    down.position.y >= it.offset &&
                                        down.position.y < it.offset + it.size
                                } ?: return@awaitEachGesture
                                val handleTop = item.offset + CardDimensions.handleHitTargetTopOffset.toPx()
                                val handleBottom = handleTop + CardDimensions.handleTouchTargetSize.toPx()
                                if (down.position.y < handleTop || down.position.y > handleBottom) {
                                    return@awaitEachGesture
                                }
                                val key = item.key as? String ?: return@awaitEachGesture
                                val row = latestRowsForPointer.value.firstOrNull { it.key == key }
                                    ?: return@awaitEachGesture
                                val anchorNote = row.notes.firstOrNull()
                                    ?: return@awaitEachGesture

                                // Observe movement in the Initial pass so the LazyColumn's
                                // scroll detector cannot steal a drag that began on the handle.
                                // The down event stays unconsumed; vertical intent claims it at slop.
                                var totalX = 0f
                                var totalY = 0f
                                var dragStartY = Float.NaN
                                while (dragStartY.isNaN()) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: break
                                    if (!change.pressed) break

                                    val delta = change.position - change.previousPosition
                                    totalX += delta.x
                                    totalY += delta.y
                                    val absX = kotlin.math.abs(totalX)
                                    val absY = kotlin.math.abs(totalY)
                                    val touchSlop = viewConfiguration.touchSlop

                                    if (absY > touchSlop && absY > absX) {
                                        change.consume()
                                        dragStartY = change.position.y
                                    } else if (absX > touchSlop && absX >= absY) {
                                        return@awaitEachGesture
                                    }
                                }
                                if (dragStartY.isNaN()) return@awaitEachGesture
                                if (latestIsDraggingForPointer.value) return@awaitEachGesture
                                latestStartDrag.value.invoke(
                                    anchorNote,
                                    dragStartY,
                                    dragStartY - item.offset
                                )

                                var released = false
                                try {
                                    var tracking = true
                                    while (tracking) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: break
                                        if (!change.pressed) {
                                            released = true
                                            dragReleaseRequested = true
                                            tracking = false
                                        } else {
                                            dragPointerY = change.position.y
                                            change.consume()
                                        }
                                    }
                                } finally {
                                    // Cancellation is not finger-up. Discard the transient session
                                    // instead of committing a partial reorder accidentally.
                                    if (!released && draggingIds.isNotEmpty()) {
                                        draggingIds = emptySet()
                                        reorderSession = null
                                        dragBaseRows = emptyMap()
                                        dragPointerY = 0f
                                        dragGrabOffsetPx = 0f
                                        dragReleaseRequested = false
                                    }
                                }
                            }
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        // A permanent keyed anchor keeps LazyColumn's first-visible
                        // item identity stable while the first two note rows exchange.
                        // Its 1dp height is visually negligible but prevents the list
                        // from following a note that is being displaced from index 0.
                        item(key = "notes_reorder_viewport_anchor") {
                            Spacer(Modifier.height(1.dp))
                        }
                        itemsIndexed(visibleDragRows, key = { _, row -> row.key }) { index, dragRow ->
                            // Keep placement animation attached to every keyed row,
                            // including index 0. Toggling it off at the first position
                            // breaks continuity when a row crosses the top boundary.
                            val rowPlacementModifier = Modifier.animateItemPlacement(
                                animationSpec = tween(durationMillis = NOTE_REORDER_ANIMATION_DURATION_MILLIS)
                            )
                            val anchorNoteId = dragRow.key.toLongOrNull()
                            val anchorNote = dragRow.notes.firstOrNull { it.id == anchorNoteId } ?: dragRow.notes.first()
                            Box(
                                Modifier.fillMaxWidth().then(rowPlacementModifier)
                            ) {
                                if (dragRow.notes.size > 1) {
                                    DraggedNotesGroup(
                                        notes = dragRow.notes,
                                        dark = dark,
                                        noteColors = noteColors,
                                        customSelectionColor = selectedNoteBackground,
                                        anchorNoteId = anchorNote.id,
                                        rowHeight = with(density) {
                                            (measuredNoteCardHeights[anchorNote.id] ?: CardDimensions.rowHeight.roundToPx()).toDp()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    val note = dragRow.notes.first()
                                    val isSelected = note.id in selectedNoteIds
                                    NoteRow(
                                        note = note, dark = dark, noteColors = noteColors,
                                        customSelectionColor = selectedNoteBackground, pinned = note.pinned, selected = isSelected,
                                        statusNow = deadlineClock,
                                        onClick = {
                                            if (selectionMode) selectedNoteIds = if (isSelected) selectedNoteIds - note.id else selectedNoteIds + note.id
                                            else onOpen(note.id)
                                        },
                                        onLongPress = { selectedNoteIds = selectedNoteIds + note.id },
                                        colorSwipeEnabled = !selectionMode && !isDragging,
                                        onChangeColor = { next -> onChangeNoteColor(note.id, next) },
                                        onMeasuredHeight = { heightPx ->
                                            if (measuredNoteCardHeights[note.id] != heightPx) {
                                                measuredNoteCardHeights[note.id] = heightPx
                                            }
                                        }
                                    )
                                }

                                // This handle layer belongs to the stable keyed row, not to
                                // NoteRow. It therefore survives the NoteRow -> group placeholder
                                // replacement that happens as soon as dragging starts.
                                if (search.isBlank()) {
                                    Box(
                                        Modifier.align(Alignment.TopEnd)
                                            .padding(top = CardDimensions.handleHitTargetTopOffset, end = CardDimensions.handleHitTargetRightInset)
                                            // Keep the touch target centered on the first title line.
                                            .size(CardDimensions.handleTouchTargetSize)
                                            .zIndex(2f)

                                    )
                                }
                            }
                        }
                    }

                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(onDismissRequest = { showDeleteConfirmation = false }, title = { Text("Удалить заметки?") },
            text = { Text("Будет удалено заметок: ${selectedNoteIds.size}.") },
            confirmButton = { TextButton(onClick = { onDeleteSelected(selectedNoteIds); selectedNoteIds = emptySet(); showDeleteConfirmation = false }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Отмена") } })
    }
    if (showNewCollectionDialog) {
        AlertDialog(onDismissRequest = { showNewCollectionDialog = false }, title = { Text("Новая подборка") },
            text = { OutlinedTextField(value = newCollectionName, onValueChange = { newCollectionName = it }, singleLine = true, placeholder = { Text("Название") }) },
            confirmButton = { TextButton(enabled = newCollectionName.isNotBlank(), onClick = {
                val name = newCollectionName.trim()
                if (name.isNotEmpty() && collections.none { it.name.equals(name, true) }) onCreateCollection(name)
                showNewCollectionDialog = false
            }) { Text("Создать") } },
            dismissButton = { TextButton(onClick = { showNewCollectionDialog = false }) { Text("Отмена") } })
    }
    if (renameCollectionId != null) {
        AlertDialog(onDismissRequest = { renameCollectionId = null }, title = { Text("Переименовать подборку") },
            text = { OutlinedTextField(value = renameCollectionName, onValueChange = { renameCollectionName = it }, singleLine = true) },
            confirmButton = { TextButton(enabled = renameCollectionName.isNotBlank(), onClick = {
                val id = renameCollectionId; val name = renameCollectionName.trim()
                if (id != null && name.isNotEmpty() && collections.none { it.id != id && it.name.equals(name, true) }) onRenameCollection(id, name)
                renameCollectionId = null
            }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { renameCollectionId = null }) { Text("Отмена") } })
    }
    if (deleteCollectionId != null) {
        AlertDialog(onDismissRequest = { deleteCollectionId = null }, title = { Text("Удалить подборку?") },
            text = { Text("Её заметки будут возвращены в основной блокнот.") },
            confirmButton = { TextButton(onClick = { deleteCollectionId?.let(onDeleteCollection); deleteCollectionId = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleteCollectionId = null }) { Text("Отмена") } })
    }
}


