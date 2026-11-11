package com.magnumold.notes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
private object AppSpacing {
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
}



/**
 * Единая система размеров карточек заметок.
 * Высота строки включает содержимое карточки и нижний разделитель.
 */
private object CardDimensions {
    val contentHeight = 88.dp
    val dividerThickness = 1.dp
    val rowHeight = contentHeight + dividerThickness

    val handleRightInset = 28.dp
    val groupTextEndInset = 64.dp

    val metadataGapLight = 4.dp
    val metadataGapDark = 5.dp
}

internal data class NoteBlock(val text: String = "", val checked: Boolean = false, val isChecklist: Boolean = false, val id: String = java.util.UUID.randomUUID().toString())
internal data class Note(
    val id: Long, val title: String, val blocks: List<NoteBlock>, val updated: Long,
    val colorIndex: Int = 0, val pinned: Boolean = false, val created: Long = updated,
    val type: RecordType = RecordType.NOTE, val status: RecordStatus = RecordStatus.WAITING,
    val dateMode: RecordDateMode? = null, val dateAt: Long? = null,
    val titleEnabled: Boolean = false, val titleText: String = "",
    val descriptionEnabled: Boolean = false, val descriptionText: String = "",
    val deadlineResumeOverride: Boolean = false, val children: List<Note> = emptyList()
)
private data class NoteCollection(val id: Long, val name: String, val notes: List<Note> = emptyList())
private data class VaultNote(val note: Note, val collectionId: Long?, val position: Int, val movedAt: Long)

private fun List<NoteBlock>.toPlainText(): String = joinToString("\n\n") { block ->
    (if (block.isChecklist) (if (block.checked) "☑ " else "☐ ") else "") + block.text
}

private fun Note.matchesSearch(query: String): Boolean =
    blocks.any { it.text.contains(query, ignoreCase = true) } ||
        titleText.contains(query, ignoreCase = true) ||
        descriptionText.contains(query, ignoreCase = true) ||
        children.any { it.matchesSearch(query) }

private fun Note.hasContent(): Boolean =
    blocks.any { it.text.isNotBlank() } ||
        titleText.isNotBlank() ||
        descriptionText.isNotBlank() ||
        children.any { it.hasContent() }

private fun Note.recordStatus(now: Long = System.currentTimeMillis()): RecordStatus =
    if (type == RecordType.UNIVERSAL) {
        RecordStatusRules.deriveUniversalStatus(children.map { it.toRecordNode(now) })
    } else if (deadlineResumeOverride) {
        status
    } else {
        RecordStatusRules.applyDeadline(status, dateMode, dateAt, now)
    }

private fun Note.toRecordNode(now: Long): RecordNode =
    RecordNode(id, type, recordStatus(now), dateMode, dateAt, children.map { it.toRecordNode(now) })

private fun formatRecordDate(timestamp: Long): String =
    java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.format(java.util.Date(timestamp))

private fun formatNoteCreationDate(timestamp: Long): String =
    java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(timestamp))

private val defaultNoteColors = listOf(
    Color(0xFF40C8E0), Color(0xFFFF9F0A), Color(0xFFFFD60A),
    Color(0xFF30D158), Color(0xFFFF375F), Color(0xFFBF5AF2)
)
private data class CustomThemeColors(
    val background: Color = Color(0xFFF2F2F7),
    val topBar: Color = Color(0xFFFFFFFF),
    val statusBar: Color = Color(0xFFFFFFFF),
    val selection: Color = Color(0xFFDCEBFF),
    val addButton: Color = Color(0xFF007AFF),
    val collectionText: Color = Color(0xFF007AFF)
)
private data class ThemeColorSets(
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
private val defaultCustomThemeColors = CustomThemeColors()
private fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)
private fun colorToHex(color: Color): String = String.format("#%08X", color.toArgbInt())
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
private fun customThemeColor(colors: CustomThemeColors, key: String): Color = when (key) {
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

private fun noteTextColor(color: Color, background: Color = Color.White): Color {
    val alpha = color.alpha
    val visibleRed = color.red * alpha + background.red * (1f - alpha)
    val visibleGreen = color.green * alpha + background.green * (1f - alpha)
    val visibleBlue = color.blue * alpha + background.blue * (1f - alpha)
    val luminance = 0.299f * visibleRed + 0.587f * visibleGreen + 0.114f * visibleBlue
    return if (luminance < 0.48f) Color.White else Color(0xFF202020)
}


private data class BackupData(val notes: List<Note>, val collections: List<NoteCollection>, val archived: List<VaultNote>, val trash: List<VaultNote>)
private fun noteToJson(note: Note): JSONObject {
    val blocks = JSONArray()
    note.blocks.forEach { block ->
        blocks.put(JSONObject().put("id", block.id).put("text", block.text)
            .put("checked", block.checked).put("checklist", block.isChecklist))
    }
    return JSONObject()
        .put("id", note.id).put("title", note.title).put("updated", note.updated)
        .put("created", note.created).put("colorIndex", note.colorIndex).put("pinned", note.pinned)
        .put("type", note.type.name).put("status", note.status.name)
        .put("dateMode", note.dateMode?.name ?: JSONObject.NULL)
        .put("dateAt", note.dateAt ?: JSONObject.NULL)
        .put("titleEnabled", note.titleEnabled).put("titleText", note.titleText)
        .put("descriptionEnabled", note.descriptionEnabled).put("descriptionText", note.descriptionText)
        .put("deadlineResumeOverride", note.deadlineResumeOverride)
        .put("blocks", blocks).put("children", notesToJson(note.children))
}
private fun notesToJson(items: List<Note>): JSONArray {
    val array = JSONArray()
    items.filter { it.hasContent() }.forEach { array.put(noteToJson(it)) }
    return array
}
private fun vaultToJson(items: List<VaultNote>): JSONArray {
    val array = JSONArray()
    items.forEach { entry ->
        array.put(JSONObject().put("collectionId", entry.collectionId ?: JSONObject.NULL)
            .put("position", entry.position).put("movedAt", entry.movedAt).put("note", noteToJson(entry.note)))
    }
    return array
}
private fun parseNoteObject(obj: JSONObject, index: Int): Note {
    val blocksJson = obj.optJSONArray("blocks") ?: JSONArray()
    var blocks = (0 until blocksJson.length()).map { blockIndex ->
        val block = blocksJson.getJSONObject(blockIndex)
        NoteBlock(block.optString("text"), block.optBoolean("checked"), block.optBoolean("checklist"),
            block.optString("id").ifBlank { java.util.UUID.randomUUID().toString() })
    }.ifEmpty { listOf(NoteBlock()) }
    val oldTitle = obj.optString("title").trim()
    if (oldTitle.isNotEmpty() && blocks.firstOrNull()?.text?.trim() != oldTitle) {
        blocks = listOf(NoteBlock(oldTitle)) + blocks
    }
    val type = runCatching { RecordType.valueOf(obj.optString("type", "NOTE")) }.getOrDefault(RecordType.NOTE)
    val status = runCatching { RecordStatus.valueOf(obj.optString("status", "WAITING")) }.getOrDefault(RecordStatus.WAITING)
    val dateMode = runCatching { RecordDateMode.valueOf(obj.optString("dateMode")) }.getOrNull()
    val children = parseNotesArray(obj.optJSONArray("children") ?: JSONArray())
    val updated = obj.optLong("updated", System.currentTimeMillis())
    val created = obj.optLong("created",
        obj.optLong("id", updated).takeIf { it > 1_000_000_000_000L } ?: updated)
    return Note(
        id = obj.optLong("id", index.toLong() + 1L), title = "", blocks = blocks, updated = updated,
        colorIndex = obj.optInt("colorIndex", 0).coerceIn(0, 6), pinned = obj.optBoolean("pinned", false),
        created = created, type = type, status = status, dateMode = dateMode,
        dateAt = if (obj.isNull("dateAt")) null else obj.optLong("dateAt"),
        titleEnabled = obj.optBoolean("titleEnabled", false), titleText = obj.optString("titleText"),
        descriptionEnabled = obj.optBoolean("descriptionEnabled", false),
        descriptionText = obj.optString("descriptionText"),
        deadlineResumeOverride = obj.optBoolean("deadlineResumeOverride", false), children = children
    )
}
private fun parseNotesArray(array: JSONArray): List<Note> =
    (0 until array.length()).map { index -> parseNoteObject(array.getJSONObject(index), index) }
        .filter { it.hasContent() }
private fun parseVaultArray(array: JSONArray?): List<VaultNote> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val obj = array.optJSONObject(index) ?: return@mapNotNull null
        val noteObj = obj.optJSONObject("note") ?: return@mapNotNull null
        val note = parseNoteObject(noteObj, index).takeIf { it.hasContent() } ?: return@mapNotNull null
        val collectionId = if (obj.isNull("collectionId")) null else obj.optLong("collectionId")
        VaultNote(note, collectionId, obj.optInt("position", 0), obj.optLong("movedAt", System.currentTimeMillis()))
    }
}
private fun createBackupJson(items: List<Note>, collections: List<NoteCollection>, archived: List<VaultNote>, trash: List<VaultNote>): String {
    val collectionArray = JSONArray()
    collections.forEach { collection ->
        collectionArray.put(JSONObject().put("id", collection.id).put("name", collection.name).put("notes", notesToJson(collection.notes)))
    }
    return JSONObject().put("format", "notes_backup").put("version", 6).put("exportedAt", System.currentTimeMillis())
        .put("notes", notesToJson(items)).put("collections", collectionArray)
        .put("archive", vaultToJson(archived)).put("trash", vaultToJson(trash)).toString(2)
}
private fun parseBackupJson(raw: String): BackupData {
    val root = JSONObject(raw)
    require(root.optString("format") == "notes_backup") { "Это не резервная копия блокнота" }
    require(root.optInt("version") in 1..6) { "Неподдерживаемая версия файла" }
    val notes = parseNotesArray(root.optJSONArray("notes") ?: error("В файле нет списка заметок"))
    val array = root.optJSONArray("collections") ?: JSONArray()
    val collections = (0 until array.length()).map { index ->
        val obj = array.getJSONObject(index)
        NoteCollection(obj.optLong("id", System.currentTimeMillis() + index),
            obj.optString("name").ifBlank { "Подборка ${index + 1}" },
            parseNotesArray(obj.optJSONArray("notes") ?: JSONArray()))
    }
    return BackupData(notes, collections, parseVaultArray(root.optJSONArray("archive")), parseVaultArray(root.optJSONArray("trash")))
}

private fun Note.sameRecordContent(other: Note): Boolean =
    type == other.type && blocks.toPlainText() == other.blocks.toPlainText() &&
        titleText == other.titleText && descriptionText == other.descriptionText

private fun mergeImportedNotes(existing: List<Note>, imported: List<Note>): List<Note> {
    val result = existing.toMutableList()
    imported.forEach { incoming ->
        val sameIndex = result.indexOfFirst { it.sameRecordContent(incoming) }
        if (sameIndex >= 0) {
            val current = result[sameIndex]
            result[sameIndex] = current.copy(
                children = mergeImportedNotes(current.children, incoming.children)
            )
            return@forEach
        }
        val collision = result.any { it.id == incoming.id }
        val safeId = if (!collision) incoming.id else {
            var candidate = System.currentTimeMillis()
            while (result.any { it.id == candidate }) candidate++
            candidate
        }
        val blocks = if (collision) incoming.blocks.map {
            it.copy(id = java.util.UUID.randomUUID().toString())
        } else incoming.blocks
        result.add(incoming.copy(id = safeId, blocks = blocks))
    }
    return result
}

private fun mergeImportedVault(existing: List<VaultNote>, imported: List<VaultNote>): List<VaultNote> {
    val result = existing.toMutableList()
    imported.forEach { incoming ->
        val sameIndex = result.indexOfFirst { it.note.sameRecordContent(incoming.note) }
        if (sameIndex >= 0) {
            val current = result[sameIndex]
            result[sameIndex] = current.copy(
                note = current.note.copy(
                    children = mergeImportedNotes(current.note.children, incoming.note.children)
                )
            )
            return@forEach
        }
        val collision = result.any { it.note.id == incoming.note.id }
        val safeNote = if (!collision) incoming.note else {
            var candidate = System.currentTimeMillis()
            while (result.any { it.note.id == candidate }) candidate++
            incoming.note.copy(id = candidate, blocks = incoming.note.blocks.map { it.copy(id = java.util.UUID.randomUUID().toString()) })
        }
        result.add(incoming.copy(note = safeNote))
    }
    return result
}

private fun mergeImportedCollections(existing: List<NoteCollection>, imported: List<NoteCollection>): List<NoteCollection> {
    val result = existing.toMutableList()
    imported.forEach { incoming ->
        val sameName = result.indexOfFirst { it.name.equals(incoming.name, true) }
        if (sameName >= 0) result[sameName] = result[sameName].copy(notes = mergeImportedNotes(result[sameName].notes, incoming.notes))
        else {
            var id = incoming.id
            while (result.any { it.id == id }) id++
            result.add(incoming.copy(id = id))
        }
    }
    return result
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
        setContent {
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
            val notes = remember { mutableStateListOf<Note>().apply { addAll(loadNotes()) } }
            val collections = remember { mutableStateListOf<NoteCollection>().apply { addAll(loadCollections()) } }
            var activeCollectionId by remember { mutableStateOf<Long?>(null) }
            var selectedId by remember { mutableStateOf<Long?>(null) }
            val activeNotes = if (activeCollectionId == null) notes.toList()
                else collections.firstOrNull { it.id == activeCollectionId }?.notes.orEmpty()
            fun updateActiveNotes(updated: List<Note>) {
                if (activeCollectionId == null) {
                    notes.clear(); notes.addAll(updated)
                } else {
                    val index = collections.indexOfFirst { it.id == activeCollectionId }
                    if (index >= 0) collections[index] = collections[index].copy(notes = updated)
                }
            }
            var autoFocusNewNote by remember { mutableStateOf(false) }
            var search by remember { mutableStateOf("") }
            var searchVisible by remember { mutableStateOf(false) }
            var settingsVisible by remember { mutableStateOf(false) }
            var vaultScreen by remember { mutableStateOf<String?>(null) }
            val archived = remember { mutableStateListOf<VaultNote>().apply { addAll(loadVault("archive")) } }
            val trash = remember { mutableStateListOf<VaultNote>().apply { addAll(loadVault("trash")) } }

            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                if (uri != null) {
                    try {
                        contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(createBackupJson(notes.toList(), collections.toList(), archived.toList(), trash.toList()).toByteArray(Charsets.UTF_8))
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
                        val imported = parseBackupJson(json)
                        val beforeCount = notes.size + collections.sumOf { it.notes.size } + archived.size + trash.size
                        val merged = mergeImportedNotes(notes.toList(), imported.notes)
                        notes.clear(); notes.addAll(merged)
                        val mergedCollections = mergeImportedCollections(collections.toList(), imported.collections)
                        collections.clear(); collections.addAll(mergedCollections)
                        archived.addAll(mergeImportedVault(archived.toList(), imported.archived))
                        trash.addAll(mergeImportedVault(trash.toList(), imported.trash))
                        val afterCount = notes.size + collections.sumOf { it.notes.size } + archived.size + trash.size
                        Toast.makeText(this, "Импортировано заметок: ${afterCount - beforeCount}", Toast.LENGTH_LONG).show()
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
            LaunchedEffect(notes.toList(), collections.toList(), archived.toList(), trash.toList()) {
                saveNotes(notes.toList()); saveCollections(collections.toList()); saveVault("archive", archived.toList()); saveVault("trash", trash.toList())
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
                        entries = if (vaultScreen == "archive") archived.toList() else trash.toList(),
                        isTrash = vaultScreen == "trash",
                        onBack = { vaultScreen = null },
                        onRestore = { entry ->
                            if (vaultScreen == "archive") archived.removeAll { it.note.id == entry.note.id } else trash.removeAll { it.note.id == entry.note.id }
                            val targetCollection = entry.collectionId?.let { id -> collections.indexOfFirst { it.id == id } } ?: -1
                            if (targetCollection >= 0) {
                                val current = collections[targetCollection].notes.toMutableList()
                                current.add(entry.position.coerceIn(0, current.size), entry.note)
                                collections[targetCollection] = collections[targetCollection].copy(notes = current)
                            } else {
                                notes.add(entry.position.coerceIn(0, notes.size), entry.note)
                            }
                        },
                        onMoveToTrash = { entry -> archived.removeAll { it.note.id == entry.note.id }; trash.add(0, entry) },
                        onDeleteForever = { entry -> trash.removeAll { it.note.id == entry.note.id } },
                        onEmptyTrash = { trash.clear() },
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
                        collections = collections.toList(), activeCollectionId = activeCollectionId,
                        onSelectCollection = { activeCollectionId = it; selectedId = null },
                        onCreateCollection = { name -> val id = System.currentTimeMillis(); collections.add(NoteCollection(id, name)); activeCollectionId = id },
                        onRenameCollection = { id, name ->
                            val index = collections.indexOfFirst { it.id == id }
                            if (index >= 0) collections[index] = collections[index].copy(name = name)
                        },
                        onDeleteCollection = { id ->
                            val collection = collections.firstOrNull { it.id == id }
                            if (collection != null) { notes.addAll(0, collection.notes); collections.removeAll { it.id == id }; if (activeCollectionId == id) activeCollectionId = null }
                        },
                        onMoveCollection = { id, delta ->
                            val from = collections.indexOfFirst { it.id == id }
                            val to = (from + delta).coerceIn(0, collections.lastIndex)
                            if (from >= 0 && from != to) { val item = collections.removeAt(from); collections.add(to, item) }
                        },
                        onTransferSelected = { ids, targetId ->
                            if (targetId != activeCollectionId) {
                                val moving = activeNotes.filter { it.id in ids }
                                updateActiveNotes(activeNotes.filterNot { it.id in ids })
                                if (targetId == null) notes.addAll(0, moving)
                                else {
                                    val targetIndex = collections.indexOfFirst { it.id == targetId }
                                    if (targetIndex >= 0) collections[targetIndex] = collections[targetIndex].copy(notes = moving + collections[targetIndex].notes)
                                }
                            }
                        },
                        onSearch = { search = it },
                        onToggleSearch = { searchVisible = !searchVisible; if (!searchVisible) search = "" },
                        dark = dark,
                        onOpenSettings = { settingsVisible = true },
                        onOpen = { selectedId = it },
                        onArchiveSelected = { ids ->
                            val moving = activeNotes.filter { it.id in ids }
                            moving.forEach { note -> archived.add(0, VaultNote(note, activeCollectionId, activeNotes.indexOfFirst { it.id == note.id }, System.currentTimeMillis())) }
                            updateActiveNotes(activeNotes.filterNot { it.id in ids })
                        },
                        onDeleteSelected = { ids ->
                            val moving = activeNotes.filter { it.id in ids }
                            moving.forEach { note -> trash.add(0, VaultNote(note, activeCollectionId, activeNotes.indexOfFirst { it.id == note.id }, System.currentTimeMillis())) }
                            updateActiveNotes(activeNotes.filterNot { it.id in ids })
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
                            val id = System.currentTimeMillis()
                            updateActiveNotes(listOf(Note(id, "", listOf(NoteBlock()), id, created = id, type = type)) + activeNotes)
                            autoFocusNewNote = true
                            selectedId = id
                        }
                    )
                } else {
                    NoteEditor(
                        note = selected,
                        dark = dark,
                        topBarColor = activeThemeColors.topBar,
                        addButtonColor = activeThemeColors.addButton,
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
                            if (promoteChildren && selected.type == RecordType.UNIVERSAL) {
                                val index = activeNotes.indexOfFirst { it.id == selected.id }
                                val updated = activeNotes.toMutableList()
                                if (index >= 0) {
                                    updated.removeAt(index)
                                    updated.addAll(index, selected.children)
                                }
                                updateActiveNotes(updated)
                            } else {
                                trash.add(0, VaultNote(selected, activeCollectionId, activeNotes.indexOfFirst { it.id == selected.id }.coerceAtLeast(0), System.currentTimeMillis()))
                                updateActiveNotes(activeNotes.filterNot { it.id == selected.id })
                            }
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

    private fun loadNotes(): List<Note> = try {
        parseNotesArray(JSONArray(getPreferences(Context.MODE_PRIVATE).getString("notes", "[]") ?: "[]"))
    } catch (_: Exception) { emptyList() }

    private fun loadVault(key: String): List<VaultNote> = try {
        parseVaultArray(JSONArray(getPreferences(Context.MODE_PRIVATE).getString(key, "[]") ?: "[]"))
    } catch (_: Exception) { emptyList() }

    private fun saveVault(key: String, items: List<VaultNote>) {
        getPreferences(Context.MODE_PRIVATE).edit().putString(key, vaultToJson(items).toString()).apply()
    }

    private fun loadCollections(): List<NoteCollection> = try {
        val array = JSONArray(getPreferences(Context.MODE_PRIVATE).getString("collections", "[]") ?: "[]")
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            NoteCollection(obj.optLong("id", System.currentTimeMillis() + i),
                obj.optString("name").ifBlank { "Подборка ${i + 1}" },
                parseNotesArray(obj.optJSONArray("notes") ?: JSONArray()))
        }
    } catch (_: Exception) { emptyList() }

    private fun saveCollections(items: List<NoteCollection>) {
        val array = JSONArray()
        items.forEach { collection ->
            array.put(JSONObject().put("id", collection.id).put("name", collection.name).put("notes", notesToJson(collection.notes)))
        }
        getPreferences(Context.MODE_PRIVATE).edit().putString("collections", array.toString()).apply()
    }

    private fun saveNotes(items: List<Note>) {
        getPreferences(Context.MODE_PRIVATE).edit()
            .putString("notes", notesToJson(items).toString()).apply()
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

@Composable
private fun SettingsScreen(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    themeColors: CustomThemeColors,
    onThemeColorChange: (String, Color) -> Unit,
    onResetThemeColors: () -> Unit,
    noteColors: List<Color>,
    noteColorOpacity: Float,
    onNoteColorsChange: (List<Color>) -> Unit,
    onNoteColorOpacityChange: (Float) -> Unit,
    onExport: () -> Unit,    onImport: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenTrash: () -> Unit,
    onBack: () -> Unit
) {
    var colorEditorIndex by remember { mutableStateOf<Int?>(null) }
    var customColorEditorKey by remember { mutableStateOf<String?>(null) }
    var personalizationVisible by remember { mutableStateOf(false) }

    BackHandler(enabled = colorEditorIndex != null || customColorEditorKey != null || personalizationVisible) {
        if (colorEditorIndex != null || customColorEditorKey != null) {
            colorEditorIndex = null
            customColorEditorKey = null
        } else {
            personalizationVisible = false
        }
    }

    if (customColorEditorKey != null) {
        val key = customColorEditorKey!!
        val title = when (key) {
            "background" -> "Фон приложения"
            "topBar" -> "Панель приложения"
            "statusBar" -> "Строка состояния"
            "selection" -> "Выделенные заметки"
            "addButton" -> "Кнопки добавления"
            else -> "Текст выбранной подборки"
        }
        ThemeColorEditorScreen(
            title = title,
            color = customThemeColor(themeColors, key),
            resetColor = customThemeColor(when (themeMode) { "light" -> ThemeColorSets().light; "dark" -> ThemeColorSets().dark; else -> defaultCustomThemeColors }, key),
            topBarColor = themeColors.topBar,
            onBack = { customColorEditorKey = null },
            onColorChange = { onThemeColorChange(key, it) },
            onReset = { onThemeColorChange(key, customThemeColor(when (themeMode) { "light" -> ThemeColorSets().light; "dark" -> ThemeColorSets().dark; else -> defaultCustomThemeColors }, key)) }
        )
        return
    }

    if (colorEditorIndex != null) {
        NoteColorEditorScreen(
            index = colorEditorIndex!!,
            color = noteColors[colorEditorIndex!!],
            topBarColor = themeColors.topBar,
            onBack = { colorEditorIndex = null },
            onColorChange = { updated ->
                onNoteColorsChange(noteColors.toMutableList().apply { this[colorEditorIndex!!] = updated })
            }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = themeColors.topBar, contentColor = noteTextColor(themeColors.topBar)) {
                Row(
                    modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                        .height(64.dp).padding(start = AppSpacing.toolbarControlInset, end = AppSpacing.toolbarControlInset),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (personalizationVisible) personalizationVisible = false else onBack() }) {
                        AppIcon(NoteIcon.Back, contentDescription = "Назад")
                    }
                    Text(
                        if (personalizationVisible) "Персонализация" else "Настройки",
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (personalizationVisible) {
                ListItem(headlineContent = { Text("Внешний вид", fontWeight = FontWeight.Medium) })
                ListItem(
                    headlineContent = { Text("Светлая тема") },
                    trailingContent = { RadioButton(selected = themeMode == "light", onClick = { onThemeModeChange("light") }) },
                    modifier = Modifier.clickable { onThemeModeChange("light") }
                )
                ListItem(
                    headlineContent = { Text("Тёмная тема") },
                    trailingContent = { RadioButton(selected = themeMode == "dark", onClick = { onThemeModeChange("dark") }) },
                    modifier = Modifier.clickable { onThemeModeChange("dark") }
                )
                ListItem(
                    headlineContent = { Text("Своя тема") },
                    supportingContent = { Text("Независимое оформление приложения") },
                    trailingContent = { RadioButton(selected = themeMode == "custom", onClick = { onThemeModeChange("custom") }) },
                    modifier = Modifier.clickable { onThemeModeChange("custom") }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                ListItem(headlineContent = { Text(if (themeMode == "custom") "Настройка своей темы" else "Настройка цветов темы", fontWeight = FontWeight.Medium) })
                val themeRows = buildList {
                    if (themeMode == "custom") add(Triple("background", "Фон приложения", themeColors.background))
                    add(Triple("topBar", "Панель приложения", themeColors.topBar))
                    add(Triple("statusBar", "Строка состояния", themeColors.statusBar))
                    add(Triple("selection", "Выделенные заметки", themeColors.selection))
                    add(Triple("addButton", "Кнопки добавления", themeColors.addButton))
                    add(Triple("collectionText", "Текст выбранной подборки", themeColors.collectionText))
                }
                themeRows.forEach { (key, label, color) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = { Box(Modifier.size(30.dp).background(color, RoundedCornerShape(7.dp))) },
                        trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.clickable { customColorEditorKey = key }
                    )
                }
                TextButton(onClick = onResetThemeColors, modifier = Modifier.padding(start = AppSpacing.screenHorizontal)) {
                    Text(if (themeMode == "custom") "Сбросить свою тему" else "Сбросить цвета темы")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                ListItem(headlineContent = { Text("Цвета заметок", fontWeight = FontWeight.Medium) })
                noteColors.forEachIndexed { index, color ->
                    ListItem(
                        headlineContent = { Text(listOf("Бирюзовый", "Оранжевый", "Жёлтый", "Зелёный", "Розовый", "Фиолетовый")[index]) },
                        leadingContent = { Box(Modifier.size(30.dp).background(color.copy(alpha = noteColorOpacity), RoundedCornerShape(7.dp))) },
                        trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.clickable { colorEditorIndex = index }
                    )
                }
                CompactOpacitySlider(value = noteColorOpacity, onValueChange = onNoteColorOpacityChange)
                TextButton(onClick = { onNoteColorsChange(defaultNoteColors) }, modifier = Modifier.padding(start = AppSpacing.screenHorizontal)) {
                    Text("Вернуть стандартные цвета")
                }
            } else {
                ListItem(
                    headlineContent = { Text("Персонализация") },
                    supportingContent = { Text("Темы и цвета интерфейса и заметок") },
                    leadingContent = { AppIcon(NoteIcon.Palette, contentDescription = null) },
                    trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { personalizationVisible = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                ListItem(headlineContent = { Text("Хранение", fontWeight = FontWeight.Medium) })
                ListItem(
                    headlineContent = { Text("Архив") },
                    supportingContent = { Text("Отложенные заметки") },
                    leadingContent = { AppIcon(NoteIcon.Archive, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenArchive)
                )
                ListItem(
                    headlineContent = { Text("Корзина") },
                    supportingContent = { Text("Удалённые заметки") },
                    leadingContent = { AppIcon(NoteIcon.Trash, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenTrash)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                ListItem(headlineContent = { Text("Данные", fontWeight = FontWeight.Medium) })
                ListItem(
                    headlineContent = { Text("Экспорт заметок") },
                    supportingContent = { Text("Сохранить резервную копию в JSON-файл") },
                    leadingContent = { AppIcon(NoteIcon.Upload, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onExport)
                )
                ListItem(
                    headlineContent = { Text("Импорт заметок") },
                    supportingContent = { Text("Добавить заметки из резервной копии") },
                    leadingContent = { AppIcon(NoteIcon.Download, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onImport)
                )
            }
        }
    }
}


@Composable
private fun CompactOpacitySlider(value: Float, onValueChange: (Float) -> Unit) {
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val activeTrack = MaterialTheme.colorScheme.primary
    val inactiveTrack = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val updateAt: (Float, Float) -> Unit = { x, width ->
        if (width > 0f) latestOnValueChange((x / width).coerceIn(0f, 1f))
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screenHorizontal, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Прозрачность", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(AppSpacing.sm))
        Canvas(
            modifier = Modifier.weight(1f).height(28.dp)
                .pointerInput(Unit) {
                    detectTapGestures { point -> updateAt(point.x, size.width.toFloat()) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { point -> updateAt(point.x, size.width.toFloat()) },
                        onDrag = { change, _ ->
                            change.consume()
                            updateAt(change.position.x, size.width.toFloat())
                        }
                    )
                }
        ) {
            val inset = 4.dp.toPx()
            val start = inset
            val end = (size.width - inset).coerceAtLeast(start)
            val centerY = size.height / 2f
            val thumbX = start + (end - start) * value.coerceIn(0f, 1f)
            drawLine(
                color = inactiveTrack,
                start = Offset(start, centerY),
                end = Offset(end, centerY),
                strokeWidth = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                color = activeTrack,
                start = Offset(start, centerY),
                end = Offset(thumbX, centerY),
                strokeWidth = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawCircle(activeTrack, radius = 4.dp.toPx(), center = Offset(thumbX, centerY))
        }
        Spacer(Modifier.width(AppSpacing.xs))
        Text(
            "${(value.coerceIn(0f, 1f) * 100f).roundToInt()}%",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeColorEditorScreen(
    title: String, color: Color, resetColor: Color, topBarColor: Color, onBack: () -> Unit,
    onColorChange: (Color) -> Unit, onReset: () -> Unit
) {
    var hex by remember(title) {
        mutableStateOf(String.format("%02X%02X%02X", (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()))
    }
    val parsedColor = remember(hex) {
        try { if (hex.length == 6) Color(android.graphics.Color.parseColor("#$hex")) else null }
        catch (_: Exception) { null }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        Surface(color = topBarColor, contentColor = noteTextColor(topBarColor)) {
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(64.dp).padding(horizontal = AppSpacing.toolbarControlInset),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { AppIcon(NoteIcon.Back, "Назад") }
                Text(title, fontWeight = FontWeight.Medium, fontSize = 18.sp)
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.screenVertical),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Box(Modifier.size(52.dp).background(parsedColor ?: color, RoundedCornerShape(10.dp)))
                Column {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text("Предпросмотр цвета", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            Text("Палитра", fontWeight = FontWeight.Medium)
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                colorPickerPresets.chunked(6).forEach { rowColors ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        rowColors.forEach { preset ->
                            val isCurrent = parsedColor == preset
                            Box(Modifier.size(42.dp).background(preset, RoundedCornerShape(9.dp))
                                .then(if (isCurrent) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(9.dp)) else Modifier)
                                .clickable {
                                    hex = String.format("%02X%02X%02X", (preset.red * 255).toInt(), (preset.green * 255).toInt(), (preset.blue * 255).toInt())
                                    onColorChange(preset)
                                })
                        }
                    }
                }
            }
            Text("HEX-код", fontWeight = FontWeight.Medium)
            OutlinedTextField(value = hex,
                onValueChange = { hex = it.filter { c -> c in "0123456789abcdefABCDEF" }.take(6).uppercase() },
                modifier = Modifier.fillMaxWidth(), singleLine = true, prefix = { Text("#") }, placeholder = { Text("RRGGBB") })
            Button(onClick = { parsedColor?.let(onColorChange) }, enabled = parsedColor != null, modifier = Modifier.fillMaxWidth()) {
                Text("Применить")
            }
            TextButton(onClick = {
                hex = colorToHex(resetColor).takeLast(6)
                onReset()
            }, modifier = Modifier.fillMaxWidth()) { Text("Вернуть стандартный цвет") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteColorEditorScreen(
    index: Int,
    color: Color,
    topBarColor: Color,
    onBack: () -> Unit,
    onColorChange: (Color) -> Unit
) {
    var hex by remember(index) {
        mutableStateOf(String.format("%02X%02X%02X", (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()))
    }
    val parsedColor = remember(hex) {
        try { if (hex.length == 6) Color(android.graphics.Color.parseColor("#$hex")) else null }
        catch (_: Exception) { null }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = topBarColor, contentColor = noteTextColor(topBarColor)) {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(64.dp).padding(horizontal = AppSpacing.toolbarControlInset),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { AppIcon(NoteIcon.Back, "Назад") }
                    Text("Изменение цвета", fontWeight = FontWeight.Medium, fontSize = 18.sp)
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.screenVertical),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Box(Modifier.size(52.dp).background(parsedColor ?: color, RoundedCornerShape(10.dp)))
                Column {
                    Text(listOf("Бирюзовый", "Оранжевый", "Жёлтый", "Зелёный", "Розовый", "Фиолетовый")[index], fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text("Предпросмотр цвета", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            Text("Палитра", fontWeight = FontWeight.Medium)
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                colorPickerPresets.chunked(6).forEach { rowColors ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        rowColors.forEach { preset ->
                            val isCurrent = parsedColor == preset
                            Box(Modifier.size(42.dp).background(preset, RoundedCornerShape(9.dp))
                                .then(if (isCurrent) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(9.dp)) else Modifier)
                                .clickable {
                                    hex = String.format("%02X%02X%02X", (preset.red * 255).toInt(), (preset.green * 255).toInt(), (preset.blue * 255).toInt())
                                    onColorChange(preset)
                                })
                        }
                    }
                }
            }
            Text("HEX-код", fontWeight = FontWeight.Medium)
            OutlinedTextField(value = hex,
                onValueChange = { value -> hex = value.filter { it in "0123456789abcdefABCDEF" }.take(6).uppercase() },
                modifier = Modifier.fillMaxWidth(), singleLine = true, prefix = { Text("#") }, placeholder = { Text("RRGGBB") })
            Button(onClick = { parsedColor?.let(onColorChange) }, enabled = parsedColor != null, modifier = Modifier.fillMaxWidth()) {
                Text("Применить")
            }
        }
    }
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
        dragRows = dragRows,
        draggingIds = draggingIds,
        reorderSession = reorderSession,
        pointerY = dragPointerY,
        grabOffsetPx = dragGrabOffsetPx,
        releaseRequested = dragReleaseRequested,
        listState = listState,
        density = density,
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
                    FloatingActionButton(
                        onClick = { createMenuExpanded = true },
                        modifier = Modifier.offset(y = (-30).dp),
                        containerColor = themeColors.addButton,
                        contentColor = noteTextColor(themeColors.addButton),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("+", fontSize = 34.sp, fontWeight = FontWeight.Light)
                    }
                    DropdownMenu(expanded = createMenuExpanded, onDismissRequest = { createMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Заметка") }, onClick = {
                            createMenuExpanded = false
                            onCreate(RecordType.NOTE)
                        })
                        DropdownMenuItem(text = { Text("Задача") }, onClick = {
                            createMenuExpanded = false
                            onCreate(RecordType.TASK)
                        })
                        DropdownMenuItem(text = { Text("Универсальный элемент") }, onClick = {
                            createMenuExpanded = false
                            onCreate(RecordType.UNIVERSAL)
                        })
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().height(42.dp).padding(start = AppSpacing.screenHorizontal, end = AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
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
                                val handleRight = size.width - CardDimensions.handleRightInset.toPx()
                                val handleLeft = handleRight - 40.dp.toPx()
                                if (down.position.x < handleLeft ||
                                    down.position.x > handleRight
                                ) return@awaitEachGesture

                                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                    down.position.y >= it.offset &&
                                        down.position.y < it.offset + it.size
                                } ?: return@awaitEachGesture
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
                            val anchorNote = dragRow.notes.first()
                            Box(
                                Modifier.fillMaxWidth().then(rowPlacementModifier)
                            ) {
                                if (dragRow.notes.size > 1) {
                                    DraggedNotesGroup(
                                        notes = dragRow.notes,
                                        dark = dark,
                                        noteColors = noteColors,
                                        customSelectionColor = selectedNoteBackground,
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
                                        onChangeColor = { next -> onChangeNoteColor(note.id, next) }
                                    )
                                }

                                // This handle layer belongs to the stable keyed row, not to
                                // NoteRow. It therefore survives the NoteRow -> group placeholder
                                // replacement that happens as soon as dragging starts.
                                if (search.isBlank()) {
                                    Box(
                                        Modifier.align(Alignment.CenterEnd)
                                            .padding(end = CardDimensions.handleRightInset)
                                            // A slightly larger touch target makes the two
                                            // narrow visual bars reliably draggable.
                                            .size(40.dp)
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

@Composable
private fun DraggedNotesGroup(
    notes: List<Note>,
    dark: Boolean,
    noteColors: List<Color>,
    customSelectionColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val anchorNote = notes.first()
    val firstLine = if (anchorNote.titleEnabled && anchorNote.titleText.isNotBlank()) anchorNote.titleText.trim()
    else anchorNote.blocks.asSequence().mapNotNull {
        it.text.lineSequence().map(String::trim).firstOrNull { line -> line.isNotEmpty() }
    }.firstOrNull() ?: "Пустая заметка"
    val groupColor = if (anchorNote.colorIndex == 0) {
        MaterialTheme.colorScheme.background
    } else {
        noteColors[anchorNote.colorIndex - 1]
    }
    val groupTextColor = if (anchorNote.colorIndex == 0) MaterialTheme.colorScheme.onSurface else noteTextColor(groupColor, MaterialTheme.colorScheme.background)
    // Regular notes use the Apple-inspired selection color; colored notes keep the previous muted overlay.
    val selectedBackground = if (anchorNote.colorIndex == 0) {
        customSelectionColor ?: if (dark) Color(0xFF1C2B40) else Color(0xFFEAF2FF)
    } else {
        lerp(groupColor, Color(0xFF808080), if (dark) 0.07f else 0.14f)
    }
    // The handle draws together when the dragged placeholder appears.
    val handleSpacing = remember { Animatable(8f) }
    LaunchedEffect(Unit) {
        handleSpacing.animateTo(4f, animationSpec = tween(durationMillis = 180))
    }

    Box(
        modifier = modifier
            .height(CardDimensions.rowHeight)
            .background(selectedBackground)
    ) {
        Box(
            Modifier.fillMaxSize().padding(start = AppSpacing.screenHorizontal, end = CardDimensions.groupTextEndInset),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (notes.size == 1 && anchorNote.type != RecordType.NOTE) {
                    RecordStatusPill(anchorNote.recordStatus(), groupTextColor)
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    if (notes.size == 1) firstLine else "Группа",
                    fontSize = 16.sp,
                    color = groupTextColor,
                    maxLines = 1
                )
            }
        }
        Box(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = CardDimensions.handleRightInset).size(30.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier.size(26.dp),
                onDraw = {
                    val stroke = 1.dp.toPx()
                    val startX = 5.dp.toPx()
                    val endX = 21.dp.toPx()
                    val centerY = size.height / 2f
                    val halfSpacing = handleSpacing.value.dp.toPx() / 2f
                    val firstY = centerY - halfSpacing
                    val secondY = centerY + halfSpacing
                    val handleColor = groupTextColor.copy(alpha = 0.65f)
                    drawLine(handleColor, Offset(startX, firstY), Offset(endX, firstY), strokeWidth = stroke)
                    drawLine(handleColor, Offset(startX, secondY), Offset(endX, secondY), strokeWidth = stroke)
                }
            )        }
        // Keep the same bottom separator that every regular note row has.
        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
            thickness = CardDimensions.dividerThickness
        )
    }
}

private fun recordStatusText(status: RecordStatus): String = when (status) {
    RecordStatus.WAITING -> "Ожидает выполнения"
    RecordStatus.IN_PROGRESS -> "В процессе"
    RecordStatus.COMPLETED -> "Выполнено"
    RecordStatus.CANCELLED -> "Отменено"
    RecordStatus.MISSED -> "Пропущено"
}

@Composable
private fun RecordStatusPill(status: RecordStatus, textColor: Color) {
    Box(
        modifier = Modifier.width(116.dp).height(24.dp)
            .background(textColor.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = recordStatusText(status),
            color = textColor.copy(alpha = 0.9f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun NoteRow(
    note: Note, dark: Boolean, noteColors: List<Color>, customSelectionColor: Color? = null, pinned: Boolean, selected: Boolean,
    statusNow: Long, onClick: () -> Unit, onLongPress: () -> Unit,
    colorSwipeEnabled: Boolean, onChangeColor: (Int) -> Unit
) {
    val firstLine = if (note.titleEnabled && note.titleText.isNotBlank()) note.titleText.trim()
    else note.blocks.asSequence().mapNotNull {
        it.text.lineSequence().map(String::trim).firstOrNull { line -> line.isNotEmpty() }
    }.firstOrNull() ?: "Пустая заметка"
    var swipeProgress by remember(note.id) { mutableFloatStateOf(0f) }
    val fillAnimation = remember(note.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val rowColor = if (note.colorIndex == 0) MaterialTheme.colorScheme.background else noteColors[note.colorIndex - 1]
    val nextColorIndex = (note.colorIndex + 1) % 7
    val nextColor = if (nextColorIndex == 0) MaterialTheme.colorScheme.background else noteColors[nextColorIndex - 1]
    var swipeFromColor by remember(note.id) { mutableStateOf(rowColor) }
    var swipeToColor by remember(note.id) { mutableStateOf(rowColor) }
    var swipeFromIndex by remember(note.id) { mutableIntStateOf(note.colorIndex) }
    var swipeToIndex by remember(note.id) { mutableIntStateOf(note.colorIndex) }
    val rowTextColor = if (note.colorIndex == 0) MaterialTheme.colorScheme.onSurface else noteTextColor(rowColor, MaterialTheme.colorScheme.background)
    val colorFraction = maxOf(swipeProgress, fillAnimation.value)
    // When entering or leaving the uncolored base state, animate only the
    // colored overlay alpha. Interpolating RGBA against an opaque base color
    // incorrectly changes the selected opacity during these two transitions.
    val displayedColor = when {
        swipeFromIndex == 0 && swipeToIndex != 0 ->
            swipeToColor.copy(alpha = swipeToColor.alpha * colorFraction)
        swipeFromIndex != 0 && swipeToIndex == 0 ->
            swipeFromColor.copy(alpha = swipeFromColor.alpha * (1f - colorFraction))
        else -> lerp(swipeFromColor, swipeToColor, colorFraction)
    }
    // Regular notes use a dedicated selection color; colored notes retain the previous muted overlay.
    val selectedBackground = if (!selected) displayedColor else if (note.colorIndex == 0) {
        customSelectionColor ?: if (dark) Color(0xFF1C2B40) else Color(0xFFEAF2FF)
    } else {
        lerp(displayedColor, Color(0xFF808080), if (dark) 0.07f else 0.14f)
    }
    Column(Modifier.fillMaxWidth().background(selectedBackground)) {
        Box(Modifier.fillMaxWidth().height(CardDimensions.contentHeight)
            .pointerInput(note.id, note.colorIndex, colorSwipeEnabled) {
                if (colorSwipeEnabled) {
                var horizontalDistance = 0f
                var colorSwipeStarted = false
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        horizontalDistance += dragAmount
                        if (horizontalDistance > 0f && !colorSwipeStarted) {
                            swipeFromColor = rowColor
                            swipeToColor = nextColor
                            swipeFromIndex = note.colorIndex
                            swipeToIndex = nextColorIndex
                            colorSwipeStarted = true
                        }
                        swipeProgress = if (horizontalDistance > 0f) (horizontalDistance / size.width.toFloat()).coerceIn(0f, 1f) else 0f
                        change.consume()
                    }, 
                    onDragEnd = {
                        val completed = horizontalDistance >= 60.dp.toPx()
                        val currentProgress = swipeProgress
                        scope.launch {
                            fillAnimation.snapTo(currentProgress)
                            if (completed) {
                                fillAnimation.animateTo(1f, tween(180))
                                onChangeColor(nextColorIndex)
                                swipeFromColor = nextColor
                                swipeToColor = nextColor
                                swipeFromIndex = nextColorIndex
                                swipeToIndex = nextColorIndex
                            } else {
                                fillAnimation.animateTo(0f, tween(160))
                                swipeFromColor = rowColor
                                swipeToColor = rowColor
                                swipeFromIndex = note.colorIndex
                                swipeToIndex = note.colorIndex
                            }
                            swipeProgress = 0f
                            fillAnimation.snapTo(0f)
                        }
                        horizontalDistance = 0f
                        colorSwipeStarted = false
                    },
                    onDragCancel = {
                        val currentProgress = swipeProgress
                        scope.launch {
                            fillAnimation.snapTo(currentProgress)
                            fillAnimation.animateTo(0f, tween(160))
                            swipeFromColor = rowColor
                            swipeToColor = rowColor
                            swipeFromIndex = note.colorIndex
                            swipeToIndex = note.colorIndex
                            swipeProgress = 0f
                            fillAnimation.snapTo(0f)
                        }
                        horizontalDistance = 0f
                        colorSwipeStarted = false
                    }
                )
                }
            }
        ) {
            Row(
                Modifier.fillMaxSize().padding(start = AppSpacing.screenHorizontal, end = CardDimensions.handleRightInset),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (note.type != RecordType.NOTE) {
                    RecordStatusPill(note.recordStatus(statusNow), rowTextColor)
                    Spacer(Modifier.width(12.dp))
                }
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .pointerInput(note.id, selected) {
                            detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() })
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(if (dark) CardDimensions.metadataGapDark else CardDimensions.metadataGapLight)) {
                        Text(
                            firstLine,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = rowTextColor,
                            maxLines = 1
                        )
                        Text(
                            formatNoteCreationDate(note.created),
                            fontSize = 12.sp,
                            color = rowTextColor.copy(alpha = 0.62f),
                            maxLines = 1
                        )
                    }
                }
                if (pinned) {
                    AppIcon(NoteIcon.Pin, contentDescription = "Закреплено", tint = rowTextColor.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Spacer(Modifier.width(18.dp))
                Box(
                    modifier = Modifier.size(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier.size(26.dp),
                        onDraw = {
                            val stroke = 1.dp.toPx()
                            val startX = 5.dp.toPx()
                            val endX = 21.dp.toPx()
                            val firstY = 9.dp.toPx()
                            val secondY = 17.dp.toPx()
                            val handleColor = rowTextColor.copy(alpha = 0.65f)
                            drawLine(handleColor, Offset(startX, firstY), Offset(endX, firstY), strokeWidth = stroke)
                            drawLine(handleColor, Offset(startX, secondY), Offset(endX, secondY), strokeWidth = stroke)
                        }
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), thickness = CardDimensions.dividerThickness)
    }
}

@OptIn(ExperimentalMaterial3Api::class)

private enum class NoteIcon {
    Back, Add, AddBlock, Search, Close, More, Palette, Archive, Trash,
    Upload, Download, Pin, Folder, Undo, Redo, Share
}

/**
 * Классические минималистичные контурные иконки Material.
 * Используется стандартная сетка Material 24×24.
 */
@Composable
private fun AppIcon(
    icon: NoteIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    val imageVector = when (icon) {
        NoteIcon.Back -> Icons.AutoMirrored.Outlined.ArrowBack
        NoteIcon.Add -> Icons.Outlined.Add
        NoteIcon.AddBlock -> Icons.Outlined.PlaylistAdd
        NoteIcon.Search -> Icons.Outlined.Search
        NoteIcon.Close -> Icons.Outlined.Close
        NoteIcon.More -> Icons.Outlined.MoreVert
        NoteIcon.Palette -> Icons.Outlined.Palette
        NoteIcon.Archive -> Icons.Outlined.Archive
        NoteIcon.Trash -> Icons.Outlined.Delete
        NoteIcon.Upload -> Icons.Outlined.FileUpload
        NoteIcon.Download -> Icons.Outlined.FileDownload
        NoteIcon.Pin -> Icons.Outlined.PushPin
        NoteIcon.Folder -> Icons.Outlined.Folder
        NoteIcon.Undo -> Icons.Outlined.Undo
        NoteIcon.Redo -> Icons.Outlined.Redo
        NoteIcon.Share -> Icons.Outlined.Share
    }

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.size(iconSize),
        tint = tint
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditor(
    note: Note, dark: Boolean, topBarColor: Color, addButtonColor: Color, autoFocus: Boolean,
    onBack: () -> Unit, onChange: (Note) -> Unit, onDelete: (Boolean) -> Unit, onShare: () -> Unit
) {
    val initialText = remember(note.id) { note.blocks.toPlainText() }
    val editorBlockId = remember(note.id) {
        note.blocks.firstOrNull()?.id ?: java.util.UUID.randomUUID().toString()
    }
    var fieldValue by remember(note.id) { mutableStateOf(TextFieldValue(initialText)) }
    var history by remember(note.id) {
        mutableStateOf(listOf(listOf(NoteBlock(initialText, id = editorBlockId))))
    }
    var historyIndex by remember(note.id) { mutableIntStateOf(0) }
    var addMenuExpanded by remember(note.id) { mutableStateOf(false) }
    var deleteDialogVisible by remember(note.id) { mutableStateOf(false) }
    var statusMenuExpanded by remember(note.id) { mutableStateOf(false) }
    var dateModeMenuExpanded by remember(note.id) { mutableStateOf(false) }
    var datePickerVisible by remember(note.id) { mutableStateOf(false) }
    var pendingDateMode by remember(note.id) { mutableStateOf(note.dateMode ?: RecordDateMode.DATE) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = note.dateAt)
    LaunchedEffect(note.id, note.dateAt) {
        datePickerState.selectedDateMillis = note.dateAt
    }
    var selectedChildId by remember(note.id) { mutableStateOf<Long?>(null) }
    var editingBlockId by remember(note.id) { mutableStateOf<String?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    BackHandler { onBack() }

    fun createChild(type: RecordType) {
        val id = System.currentTimeMillis()
        val child = Note(id, "", listOf(NoteBlock()), id, created = id, type = type)
        onChange(note.copy(children = listOf(child) + note.children))
        selectedChildId = id
        addMenuExpanded = false
    }

    LaunchedEffect(note.id, note.blocks) {
        val latestText = note.blocks.toPlainText()
        if (fieldValue.text != latestText) {
            fieldValue = TextFieldValue(latestText, selection = TextRange(latestText.length))
        }
    }

    LaunchedEffect(note.id, note.type, note.dateMode, note.dateAt, note.status, note.deadlineResumeOverride) {
        if (note.type == RecordType.TASK) {
            val effectiveStatus = note.recordStatus()
            if (effectiveStatus != note.status) onChange(note.copy(status = effectiveStatus))
        }
    }

    val selectedChild = note.children.firstOrNull { it.id == selectedChildId }
    if (selectedChild != null) {
        NoteEditor(
            note = selectedChild, dark = dark, topBarColor = topBarColor,
            addButtonColor = addButtonColor, autoFocus = false,
            onBack = {
                if (!selectedChild.hasContent()) {
                    onChange(note.copy(children = note.children.filterNot { it.id == selectedChild.id }))
                }
                selectedChildId = null
            },
            onChange = { changed ->
                onChange(note.copy(children = note.children.map { if (it.id == changed.id) changed else it }))
            },
            onDelete = { promoteChildren ->
                val index = note.children.indexOfFirst { it.id == selectedChild.id }
                val updated = note.children.toMutableList()
                if (index >= 0) {
                    updated.removeAt(index)
                    if (promoteChildren) updated.addAll(index, selectedChild.children)
                }
                onChange(note.copy(children = updated))
                selectedChildId = null
            },
            onShare = onShare
        )
        return
    }
 
    fun publish(blocks: List<NoteBlock>) {
        onChange(note.copy(blocks = blocks))
    }

    fun record(blocks: List<NoteBlock>) {
        val next = (history.take(historyIndex + 1) + listOf(blocks)).takeLast(80)
        history = next
        historyIndex = history.lastIndex
        publish(blocks)
    }

    fun updateText(value: String) {
        val changed = listOf(NoteBlock(value, id = editorBlockId))
        if (editingBlockId != editorBlockId) {
            val next = (history.take(historyIndex + 1) + listOf(changed)).takeLast(80)
            history = next
            historyIndex = history.lastIndex
            editingBlockId = editorBlockId
        } else {
            val next = history.toMutableList()
            next[historyIndex] = changed
            history = next
        }
        publish(changed)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(NoteIcon.Back, "Назад") } },
                actions = {
                    if (note.type != RecordType.NOTE) {
                        IconButton(onClick = { onChange(note.copy(titleEnabled = !note.titleEnabled)) }) {
                            Icon(
                                Icons.Outlined.Title,
                                contentDescription = "Название",
                                tint = if (note.titleEnabled) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                        IconButton(onClick = { onChange(note.copy(descriptionEnabled = !note.descriptionEnabled)) }) {
                            Icon(
                                Icons.Outlined.Description,
                                contentDescription = "Описание",
                                tint = if (note.descriptionEnabled) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (historyIndex > 0) {
                                editingBlockId = null
                                historyIndex--
                                publish(history[historyIndex])
                            }
                        },
                        enabled = historyIndex > 0
                    ) { AppIcon(NoteIcon.Undo, "Отменить") }
                    IconButton(
                        onClick = {
                            if (historyIndex < history.lastIndex) {
                                editingBlockId = null
                                historyIndex++
                                publish(history[historyIndex])
                            }
                        },
                        enabled = historyIndex < history.lastIndex
                    ) { AppIcon(NoteIcon.Redo, "Повторить") }
                    IconButton(onClick = {
                        if (note.type == RecordType.UNIVERSAL && note.children.isNotEmpty()) {
                            deleteDialogVisible = true
                        } else {
                            onDelete(false)
                        }
                    }) { AppIcon(NoteIcon.Trash, "Удалить") }
                    IconButton(onClick = onShare) { AppIcon(NoteIcon.Share, "Поделиться") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    scrolledContainerColor = topBarColor,
                    navigationIconContentColor = noteTextColor(topBarColor),
                    titleContentColor = noteTextColor(topBarColor),
                    actionIconContentColor = noteTextColor(topBarColor)
                )
            )
        },
        floatingActionButton = {
            if (note.type == RecordType.UNIVERSAL) {
                Box(modifier = Modifier.offset(y = (-30).dp)) {
                    FloatingActionButton(
                        onClick = { addMenuExpanded = true },
                        containerColor = addButtonColor,
                        contentColor = noteTextColor(addButtonColor),
                        shape = RoundedCornerShape(50)
                    ) {
                        AppIcon(NoteIcon.AddBlock, contentDescription = "Добавить запись", modifier = Modifier.size(28.dp))
                    }
                    DropdownMenu(expanded = addMenuExpanded, onDismissRequest = { addMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Задача") }, onClick = { createChild(RecordType.TASK) })
                        DropdownMenuItem(text = { Text("Универсальный элемент") }, onClick = { createChild(RecordType.UNIVERSAL) })
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = AppSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(top = AppSpacing.sm, bottom = AppSpacing.lg)
        ) {
            if (note.type != RecordType.NOTE) {
                item(key = "record_options") {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        if (note.titleEnabled) {
                            OutlinedTextField(
                                value = note.titleText,
                                onValueChange = { onChange(note.copy(titleText = it)) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Название") },
                                singleLine = true
                            )
                        }
                        if (note.descriptionEnabled) {
                            OutlinedTextField(
                                value = note.descriptionText,
                                onValueChange = { onChange(note.copy(descriptionText = it)) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Описание") },
                                minLines = 2
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Дата", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Spacer(Modifier.weight(1f))
                            Box {
                                TextButton(onClick = { dateModeMenuExpanded = true }) {
                                    val dateText = note.dateAt?.let { formatRecordDate(it) }
                                    Text(
                                        when {
                                            dateText == null -> "Добавить"
                                            note.dateMode == RecordDateMode.DEADLINE -> "Срок: $dateText"
                                            else -> dateText
                                        }
                                    )
                                }
                                DropdownMenu(
                                    expanded = dateModeMenuExpanded,
                                    onDismissRequest = { dateModeMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(text = { Text("Обычная дата") }, onClick = {
                                        pendingDateMode = RecordDateMode.DATE
                                        dateModeMenuExpanded = false
                                        datePickerVisible = true
                                    })
                                    DropdownMenuItem(text = { Text("Срок выполнения") }, onClick = {
                                        pendingDateMode = RecordDateMode.DEADLINE
                                        dateModeMenuExpanded = false
                                        datePickerVisible = true
                                    })
                                    if (note.dateAt != null) {
                                        DropdownMenuItem(text = { Text("Убрать дату") }, onClick = {
                                            dateModeMenuExpanded = false
                                            onChange(note.copy(dateMode = null, dateAt = null))
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "record_status") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Статус", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        Box {
                            TextButton(
                                onClick = { if (note.type == RecordType.TASK) statusMenuExpanded = true },
                                enabled = note.type == RecordType.TASK
                            ) {
                                Text(recordStatusText(note.recordStatus()))
                            }
                            DropdownMenu(
                                expanded = statusMenuExpanded && note.type == RecordType.TASK,
                                onDismissRequest = { statusMenuExpanded = false }
                            ) {
                                RecordStatus.values().forEach { status ->
                                    DropdownMenuItem(
                                        text = { Text(recordStatusText(status)) },
                                        onClick = {
                                            statusMenuExpanded = false
                                            val resumingMissed = note.recordStatus() == RecordStatus.MISSED && status != RecordStatus.MISSED
                                            onChange(note.copy(status = status, deadlineResumeOverride = resumingMissed))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "record_body") {
                val bodyFocusRequester = remember(note.id) { FocusRequester() }
                LaunchedEffect(note.id, autoFocus) {
                    if (autoFocus) {
                        bodyFocusRequester.requestFocus()
                        keyboardController?.show()
                    }
                }
                val editorTextStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
                BasicTextField(
                    value = fieldValue,
                    onValueChange = {
                        fieldValue = it
                        updateText(it.text)
                    },
                    modifier = Modifier.fillMaxWidth()
                        .focusRequester(bodyFocusRequester)
                        .heightIn(min = 160.dp)
                        .onFocusChanged { state ->
                            if (!state.isFocused && editingBlockId == editorBlockId) editingBlockId = null
                        },
                    textStyle = editorTextStyle,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                            if (fieldValue.text.isEmpty()) {
                                Text(
                                    "Начните писать…",
                                    style = editorTextStyle.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                    )
                                )
                            }
                            inner()
                        }
                    }
                )
            }
            if (note.type == RecordType.UNIVERSAL) {
                if (note.children.isEmpty()) {
                    item(key = "empty_nested_records") {
                        Text(
                            "Пока нет вложенных записей",
                            modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.md),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
                items(note.children, key = { "nested_${it.id}" }) { child ->
                    val childText = if (child.titleEnabled && child.titleText.isNotBlank()) child.titleText
                    else child.blocks.asSequence().mapNotNull { block ->
                        block.text.lineSequence().map(String::trim).firstOrNull { line -> line.isNotEmpty() }
                    }.firstOrNull() ?: "Пустая запись"
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selectedChildId = child.id }
                            .padding(vertical = AppSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RecordStatusPill(child.recordStatus(), MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(AppSpacing.sm))
                        Text(
                            childText,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            maxLines = 2
                        )
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "Открыть", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
            }
        }
    }
    if (deleteDialogVisible) {
        AlertDialog(
            onDismissRequest = { deleteDialogVisible = false },
            title = { Text("Удалить универсальный элемент?") },
            text = { Text("Можно удалить всё вложенное содержимое или перенести вложенные записи на уровень выше.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteDialogVisible = false
                    onDelete(false)
                }) { Text("Удалить вместе с содержимым") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { deleteDialogVisible = false }) { Text("Отмена") }
                    TextButton(onClick = {
                        deleteDialogVisible = false
                        onDelete(true)
                    }) { Text("Поднять записи выше") }
                }
            }
        )
    }
    if (datePickerVisible) {
        DatePickerDialog(
            onDismissRequest = { datePickerVisible = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedDate = datePickerState.selectedDateMillis
                    if (selectedDate != null) {
                        onChange(note.copy(
                            status = if (note.recordStatus() == RecordStatus.MISSED) RecordStatus.MISSED else note.status,
                            dateMode = pendingDateMode,
                            dateAt = selectedDate,
                            deadlineResumeOverride = false
                        ))
                    }
                    datePickerVisible = false
                }) { Text("Готово") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerVisible = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun ChecklistMark(checked: Boolean, onClick: () -> Unit) {
    val markColor = MaterialTheme.colorScheme.onSurfaceVariant
    val checkColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier.width(22.dp).height(24.dp),
        contentAlignment = Alignment.CenterStart,
        content = {
            Canvas(
                modifier = Modifier.size(18.dp).pointerInput(checked) {
                    detectTapGestures { point ->
                        val radius = size.width * (3.5f / 18f)
                        val nearestX = point.x.coerceIn(radius, size.width - radius)
                        val nearestY = point.y.coerceIn(radius, size.height - radius)
                        val dx = point.x - nearestX
                        val dy = point.y - nearestY
                        if (dx * dx + dy * dy <= radius * radius) onClick()
                    }
                },
                onDraw = {
                    val stroke = 1.5.dp.toPx()
                    val radius = 3.5.dp.toPx()
                    val inset = stroke / 2f
                    drawRoundRect(
                        color = markColor,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                        style = if (checked) androidx.compose.ui.graphics.drawscope.Fill
                        else androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                    )
                    if (checked) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width * 0.23f, size.height * 0.51f)
                            lineTo(size.width * 0.42f, size.height * 0.69f)
                            lineTo(size.width * 0.78f, size.height * 0.32f)
                        }
                        drawPath(
                            path = path,
                            color = checkColor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 1.8.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            )
                        )
                    }
                }
            )
        }
    )
}