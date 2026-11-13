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

@Composable
internal fun NotesApp(
    activity: MainActivity,
    recordsViewModel: NotesViewModel,
    savedThemeMode: String,
    initialThemeColorSets: ThemeColorSets
) {
            val snapshot by recordsViewModel.snapshot.collectAsStateWithLifecycle()
            val themeSettings = remember(activity) {
                ThemeSettingsStore(activity.getSharedPreferences("notes_settings", Context.MODE_PRIVATE))
            }
            val backupFileStore = remember(activity) {
                BackupFileStore(activity.contentResolver)
            }
            val themeState = remember(themeSettings, savedThemeMode, initialThemeColorSets) {
                AppThemeState(
                    initialMode = savedThemeMode,
                    initialColorSets = initialThemeColorSets,
                    initialNoteColors = themeSettings.loadNoteColors(),
                    initialNoteColorOpacities = themeSettings.loadNoteColorOpacities()
                )
            }
            val themeMode = themeState.mode
            val themeColorSets = themeState.colorSets
            val activeThemeColors = themeState.activeColors
            val dark = themeState.isDark
            val noteColors = themeState.noteColors
            val activeNoteColorOpacity = themeState.activeNoteColorOpacity
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
                        backupFileStore.writeBackup(uri, snapshot)
                        Toast.makeText(activity, "Резервная копия сохранена", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(activity, "Не удалось сохранить резервную копию", Toast.LENGTH_LONG).show()
                    }
                }
            }
            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    try {
                        val json = backupFileStore.readBackup(uri)
                        val importedCount = recordsViewModel.importBackup(json)
                        Toast.makeText(activity, "Импортировано заметок: $importedCount", Toast.LENGTH_LONG).show()
                    } catch (_: Exception) {
                        Toast.makeText(activity, "Не удалось импортировать файл: неверный формат", Toast.LENGTH_LONG).show()
                    }
                }
            }

            BackHandler(enabled = settingsVisible && vaultScreen != null) { vaultScreen = null }
            BackHandler(enabled = settingsVisible && vaultScreen == null) { settingsVisible = false }

            SideEffect {
                val appBackground = if (themeMode == "custom") activeThemeColors.background else if (dark) Color.Black else Color.White
                val statusBarBackground = activeThemeColors.statusBar
                activity.window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(appBackground.toArgbInt()))
                activity.window.statusBarColor = statusBarBackground.toArgbInt()
                val flags = activity.window.decorView.systemUiVisibility
                val darkStatusIcons = noteTextColor(statusBarBackground) == Color(0xFF202020)
                activity.window.decorView.systemUiVisibility = if (darkStatusIcons) {
                    flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else {
                    flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
            }
            LaunchedEffect(themeMode, dark) {
                activity.getPreferences(Context.MODE_PRIVATE).edit().putString("theme_mode", themeMode)
                    .putBoolean("dark_v2", dark).apply()
            }
            LaunchedEffect(themeColorSets) { themeSettings.saveThemeColorSets(themeColorSets) }
            LaunchedEffect(noteColors) { themeSettings.saveNoteColors(noteColors) }
            LaunchedEffect(themeState.noteColorOpacities) { themeSettings.saveNoteColorOpacities(themeState.noteColorOpacities) }

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
                        onThemeModeChange = { themeState.changeMode(it) },
                        themeColors = activeThemeColors,
                        onThemeColorChange = { key, color -> themeState.updateThemeColor(key, color) },
                        onResetThemeColors = { themeState.resetThemeColors() },
                        noteColors = noteColors,
                        noteColorOpacity = activeNoteColorOpacity,
                        onNoteColorsChange = { themeState.replaceNoteColors(it) },
                        onNoteColorOpacityChange = { opacity -> themeState.setNoteColorOpacity(opacity) },
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
                        onShare = { activity.shareNote(selected) }
                    )
                }
            }
}

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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultScreen(
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
