package com.magnumold.notes

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
                            recordsViewModel.createCollection(id, name)
                            navigation.activeCollectionId = id
                        },
                        onRenameCollection = { id, name ->
                            recordsViewModel.renameCollection(id, name)
                        },
                        onDeleteCollection = { id ->
                            recordsViewModel.deleteCollection(id)
                            if (navigation.activeCollectionId == id) navigation.activeCollectionId = null
                        },
                        onMoveCollection = { id, delta ->
                            recordsViewModel.moveCollection(id, delta)
                        },
                        onTransferSelected = { ids, targetId ->
                            if (targetId != activeCollectionId) {
                                recordsViewModel.transferRecords(activeCollectionId, targetId, ids)
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
                            recordsViewModel.reorderActiveRecords(activeCollectionId, ids, afterId)
                        },
                        onTogglePinned = { ids ->
                            recordsViewModel.toggleActiveRecordsPinned(activeCollectionId, ids)
                        },
                        onChangeNoteColor = { noteId, colorIndex ->
                            recordsViewModel.changeActiveRecordColor(activeCollectionId, noteId, colorIndex)
                        },
                        onCreate = { type ->
                            val now = System.currentTimeMillis()
                            val id = newRecordId()
                            recordsViewModel.createActiveRecord(activeCollectionId, Note(id, "", listOf(NoteBlock()), now, created = now, type = type))
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
                            if (!selected.hasContent()) recordsViewModel.removeActiveRecord(activeCollectionId, selected.id)
                            navigation.selectedId = null
                            navigation.autoFocusNewNote = false
                        },
                        onChange = { changed ->
                            val index = activeNotes.indexOfFirst { it.id == changed.id }
                            if (index >= 0) recordsViewModel.updateActiveRecord(activeCollectionId, changed.copy(updated = System.currentTimeMillis()))
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
