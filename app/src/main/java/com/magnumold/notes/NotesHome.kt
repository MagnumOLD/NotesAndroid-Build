package com.magnumold.notes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NotesHome(
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
    var showCreationDates by remember { mutableStateOf(false) }
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

    AppPageScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = homeBarColor, contentColor = homeContentColor) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .height(AppPageLayout.toolbarHeight)
                        .padding(start = AppSpacing.screenHorizontal, end = AppSpacing.toolbarControlInset),
                    verticalAlignment = Alignment.CenterVertically                ) {
                    if (selectionMode) {
                        Text("${selectedNoteIds.size} выбрано", style = AppTypography.screenTitleStyle())
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
                        Text("Заметки", style = AppTypography.screenTitleStyle())
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
            Row(Modifier.fillMaxWidth().height(AppCollectionRowDimensions.height).padding(start = AppSpacing.screenHorizontal, end = AppSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                    Text("Все",
                        color = if (activeCollectionId == null) themeColors.collectionText else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = AppTypography.sectionLabelStyle(), fontWeight = if (activeCollectionId == null) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.pointerInput(activeCollectionId) { detectTapGestures(onTap = { onSelectCollection(null); selectedNoteIds = emptySet() }) })
                    collections.forEach { collection ->
                        Box {
                            Text(collection.name,
                                color = if (activeCollectionId == collection.id) themeColors.collectionText else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = AppTypography.sectionLabelStyle(), fontWeight = if (activeCollectionId == collection.id) FontWeight.SemiBold else FontWeight.Normal,
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
                                val handleLeft = handleRight - CardDimensions.handleTouchTargetSize.toPx()
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
                                        showCreationDate = showCreationDates,
                                        onToggleCreationDate = { showCreationDates = !showCreationDates },
                                        onTogglePinned = { onTogglePinned(setOf(note.id)) },
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
                                            // Match the top control slot in the right-side card rail.
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


