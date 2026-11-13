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
            val navigation = remember { AppNavigationState() }
            val activeCollectionId = navigation.activeCollectionId
            val selectedId = navigation.selectedId
            val activeNotes = if (navigation.activeCollectionId == null) notes.toList()
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
            val autoFocusNewNote = navigation.autoFocusNewNote
            val search = navigation.search
            val searchVisible = navigation.searchVisible
            val settingsVisible = navigation.settingsVisible
            val vaultScreen = navigation.vaultScreen
            val archived = snapshot.archived
            val trash = snapshot.trash

            val backupActions = rememberBackupActions(
                activity = activity,
                snapshot = snapshot,
                recordsViewModel = recordsViewModel,
                backupFileStore = backupFileStore
            )

            BackHandler(enabled = settingsVisible && vaultScreen != null) { navigation.vaultScreen = null }
            BackHandler(enabled = settingsVisible && navigation.vaultScreen == null) { navigation.settingsVisible = false }

            AppThemeEffects(
                activity = activity,
                themeMode = themeMode,
                dark = dark,
                themeColors = activeThemeColors,
                themeColorSets = themeColorSets,
                noteColors = noteColors,
                noteColorOpacities = themeState.noteColorOpacities,
                themeSettings = themeSettings
            )

            NotesTheme(dark, activeThemeColors, themeMode == "custom") {
                val selected = activeNotes.firstOrNull { it.id == selectedId }
                if (settingsVisible && selected == null && vaultScreen != null) {
                    VaultScreen(
                        title = if (navigation.vaultScreen == "archive") "Архив" else "Корзина",
                        entries = if (navigation.vaultScreen == "archive") archived else trash,
                        isTrash = navigation.vaultScreen == "trash",
                        onBack = { navigation.vaultScreen = null },
                        onRestore = { entry -> recordsViewModel.restoreVaultEntry(entry, fromArchive = navigation.vaultScreen == "archive") },
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
                        onExport = backupActions.export,
                        onImport = backupActions.import,
                        onOpenArchive = { navigation.vaultScreen = "archive" },
                        onOpenTrash = { navigation.vaultScreen = "trash" },
                        onBack = { navigation.settingsVisible = false }
                    )
                } else if (selected == null) {
                    NotesHome(
                        notes = activeNotes, search = search, searchVisible = searchVisible,
                        noteColors = displayedNoteColors,
                        themeColors = activeThemeColors,
                        collections = collections, activeCollectionId = activeCollectionId,
                        onSelectCollection = { navigation.activeCollectionId = it; navigation.selectedId = null },
                        onCreateCollection = { name ->
                            val id = newRecordId()
                            recordsViewModel.updateSnapshot { CollectionOperations.create(it, id, name) }
                            navigation.activeCollectionId = id
                        },
                        onRenameCollection = { id, name ->
                            recordsViewModel.updateSnapshot { CollectionOperations.rename(it, id, name) }
                        },
                        onDeleteCollection = { id ->
                            recordsViewModel.updateSnapshot { CollectionOperations.delete(it, id) }
                            if (navigation.activeCollectionId == id) navigation.activeCollectionId = null
                        },
                        onMoveCollection = { id, delta ->
                            recordsViewModel.updateSnapshot { CollectionOperations.move(it, id, delta) }
                        },
                        onTransferSelected = { ids, targetId ->
                            if (targetId != activeCollectionId) {
                                recordsViewModel.updateSnapshot {
                                    CollectionOperations.transfer(it, activeCollectionId, targetId, ids)
                                }
                            }
                        },
                        onSearch = { navigation.search = it },
                        onToggleSearch = { navigation.searchVisible = !searchVisible; if (!searchVisible) navigation.search = "" },
                        dark = dark,
                        onOpenSettings = { navigation.settingsVisible = true },
                        onOpen = { navigation.autoFocusNewNote = false; navigation.selectedId = it },
                        onArchiveSelected = { ids ->
                            recordsViewModel.moveActiveRecordsToVault(activeCollectionId, ids, toTrash = false)
                        },
                        onDeleteSelected = { ids ->
                            recordsViewModel.moveActiveRecordsToVault(activeCollectionId, ids, toTrash = true)
                        },
                        onMoveSelected = { ids, afterId ->
                            recordsViewModel.updateSnapshot {
                                CollectionOperations.reorder(it, activeCollectionId, ids, afterId)
                            }
                        },
                        onTogglePinned = { ids ->
                            recordsViewModel.updateSnapshot {
                                CollectionOperations.togglePinned(it, activeCollectionId, ids)
                            }
                        },
                        onChangeNoteColor = { noteId, colorIndex ->
                            recordsViewModel.updateSnapshot {
                                CollectionOperations.changeColor(it, activeCollectionId, noteId, colorIndex)
                            }
                        },
                        onCreate = { type ->
                            val now = System.currentTimeMillis()
                            val id = newRecordId()
                            updateActiveNotes(listOf(Note(id, "", listOf(NoteBlock()), now, created = now, type = type)) + activeNotes)
                            navigation.autoFocusNewNote = true
                            navigation.selectedId = id
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
                            navigation.selectedId = null
                            navigation.autoFocusNewNote = false
                        },
                        onChange = { changed ->
                            val index = activeNotes.indexOfFirst { it.id == changed.id }
                            if (index >= 0) updateActiveNotes(activeNotes.toMutableList().apply { this[index] = changed.copy(updated = System.currentTimeMillis()) })
                        },
                        onDelete = { promoteChildren ->
                            recordsViewModel.moveActiveRecordsToVault(activeCollectionId, setOf(selected.id), toTrash = true)
                            navigation.selectedId = null
                            navigation.autoFocusNewNote = false
                        },
                        onShare = { activity.shareNote(selected) }
                    )
                }
            }
}
