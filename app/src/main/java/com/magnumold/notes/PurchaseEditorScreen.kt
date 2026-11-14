package com.magnumold.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun PurchaseEditor(
    note: Note, dark: Boolean, topBarColor: Color, addButtonColor: Color, selectionColor: Color, autoFocus: Boolean,
    isPurchaseListItem: Boolean = false,
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
    var purchaseDetailsExpanded by remember(note.id) { mutableStateOf(false) }
    var dateModeMenuExpanded by remember(note.id) { mutableStateOf(false) }
    var datePickerVisible by remember(note.id) { mutableStateOf(false) }
    var pendingDateMode by remember(note.id) { mutableStateOf(note.dateMode ?: RecordDateMode.DATE) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = note.dateAt)
    LaunchedEffect(note.id, note.dateAt) {
        datePickerState.selectedDateMillis = note.dateAt
    }
    var selectedChildId by remember(note.id) { mutableStateOf<Long?>(null) }
    var autoFocusChildId by remember(note.id) { mutableStateOf<Long?>(null) }
    var parentAutoFocusConsumed by remember(note.id) { mutableStateOf(false) }
    var selectedChildIds by remember(note.id) { mutableStateOf<Set<Long>>(emptySet()) }
    var editingBlockId by remember(note.id) { mutableStateOf<String?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val latestPurchaseNote = rememberUpdatedState(note)
    val latestPurchaseChildren = rememberUpdatedState(note.children)
    val latestPurchaseOnChange = rememberUpdatedState(onChange)
    var childReorderSession by remember(note.id) { mutableStateOf<ReorderSession?>(null) }
    var childDragBaseRows by remember(note.id) { mutableStateOf<Map<String, ChildDragRow>>(emptyMap()) }
    var childDragPointerY by remember(note.id) { mutableFloatStateOf(0f) }
    var childDragGrabOffsetPx by remember(note.id) { mutableFloatStateOf(0f) }
    var childDragReleaseRequested by remember(note.id) { mutableStateOf(false) }

    BackHandler {
        if (selectedChildIds.isNotEmpty()) selectedChildIds = emptySet() else onBack()
    }

    fun createChild(type: RecordType) {
        selectedChildIds = emptySet()
        val now = System.currentTimeMillis()
        val id = newRecordId()
        val child = Note(id, "", listOf(NoteBlock()), now, created = now, type = type)
        onChange(withUpdatedRecordChildren(note, listOf(child) + note.children))
        selectedChildId = id
        autoFocusChildId = id
    }

    LaunchedEffect(note.id, note.blocks) {
        val latestText = note.blocks.toPlainText()
        if (fieldValue.text != latestText) {
            fieldValue = TextFieldValue(latestText, selection = TextRange(latestText.length))
        }
    }

    LaunchedEffect(note.id, note.type, note.dateMode, note.dateAt, note.status, note.deadlineResumeOverride) {
        if (note.type == RecordType.PURCHASE) {
            val effectiveStatus = note.recordStatus()
            if (effectiveStatus != note.status) onChange(note.copy(status = effectiveStatus))
        }
    }

    val selectedChild = note.children.firstOrNull { it.id == selectedChildId }
    if (selectedChild != null) {
        PurchaseEditor(
            note = selectedChild, dark = dark, topBarColor = topBarColor,
            addButtonColor = addButtonColor, selectionColor = selectionColor,
            autoFocus = selectedChild.id == autoFocusChildId, isPurchaseListItem = true,
            onBack = {
                if (!selectedChild.hasContent()) {
                    onChange(note.copy(children = note.children.filterNot { it.id == selectedChild.id }))
                }
                selectedChildId = null
                autoFocusChildId = null
            },
            onChange = { changed ->
                onChange(withUpdatedRecordChildren(note, note.children.map { if (it.id == changed.id) changed else it }))
            },
            onDelete = { promoteChildren ->
                val index = note.children.indexOfFirst { it.id == selectedChild.id }
                val updated = note.children.toMutableList()
                if (index >= 0) {
                    updated.removeAt(index)
                    if (promoteChildren) updated.addAll(index, selectedChild.children)
                }
                onChange(withUpdatedRecordChildren(note, updated))
                selectedChildId = null
                autoFocusChildId = null
            },
            onShare = onShare
        )
        return
    }
 
    fun resetChildDrag() {
        childReorderSession = null
        childDragBaseRows = emptyMap()
        childDragPointerY = 0f
        childDragGrabOffsetPx = 0f
        childDragReleaseRequested = false
    }

    fun finishChildDrag() {
        val session = childReorderSession
        if (session != null) {
            val currentChildren = latestPurchaseChildren.value
            val reordered = session.keys.flatMap { childDragBaseRows[it]?.notes.orEmpty() }
            if (reordered.size == currentChildren.size &&
                reordered.map { it.id }.toSet() == currentChildren.map { it.id }.toSet()
            ) {
                latestPurchaseOnChange.value.invoke(
                    withUpdatedRecordChildren(latestPurchaseNote.value, reordered)
                )
            }
        }
        resetChildDrag()
    }

    val displayedChildRows = childReorderSession?.keys
        ?.mapNotNull { childDragBaseRows[it] }
        ?: note.children.map { ChildDragRow(listOf(it), "nested_${it.id}") }

    AdjacentReorderEffect(
        isDragging = childReorderSession != null,
        orderedKeys = childReorderSession?.keys.orEmpty(),
        reorderSession = childReorderSession,
        pointerY = childDragPointerY,
        grabOffsetPx = childDragGrabOffsetPx,
        releaseRequested = childDragReleaseRequested,
        listState = listState,
        density = LocalDensity.current,
        onSessionChange = { childReorderSession = it },
        onFinishDrag = { finishChildDrag() }
    )

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
            if (note.type == RecordType.PURCHASE && !isPurchaseListItem) {
                ParentRecordHeader(
                    note = note,
                    placeholder = "Название покупки",
                    topBarColor = topBarColor,
                    autoFocus = autoFocus && !parentAutoFocusConsumed,
                    onAutoFocusConsumed = { parentAutoFocusConsumed = true },
                    selectedCount = selectedChildIds.size,
                    onClearSelection = { selectedChildIds = emptySet() },
                    onDelete = { onDelete(false) },
                    onOpenStatistics = {},
                    onShare = onShare,
                    onChange = onChange
                )
            } else {
                TopAppBar(
                    title = { Text(if (isPurchaseListItem) "Товар" else "Заметка", fontWeight = FontWeight.Medium, fontSize = 18.sp) },
                    navigationIcon = {},
                    actions = {
                        if (isPurchaseListItem) {
                            IconButton(onClick = { purchaseDetailsExpanded = !purchaseDetailsExpanded }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = if (purchaseDetailsExpanded) "Скрыть дату" else "Показать дату")
                            }
                        }
                        if (!isPurchaseListItem && note.type != RecordType.NOTE) {
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
                        IconButton(onClick = { onDelete(false) }) { AppIcon(NoteIcon.Trash, "Удалить") }
                        if (!isPurchaseListItem) IconButton(onClick = onShare) { AppIcon(NoteIcon.Share, "Поделиться") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = topBarColor,
                        scrolledContainerColor = topBarColor,
                        navigationIconContentColor = noteTextColor(topBarColor),
                        titleContentColor = noteTextColor(topBarColor),
                        actionIconContentColor = noteTextColor(topBarColor)
                    )
                )
            }
        },
        floatingActionButton = {
            if (note.type == RecordType.PURCHASE && !isPurchaseListItem) {
                Box(modifier = Modifier.offset(y = PrimaryAddButtonDimensions.verticalOffset)) {
                    FloatingActionButton(
                        modifier = Modifier.size(PrimaryAddButtonDimensions.diameter),
                        onClick = { createChild(RecordType.PURCHASE) },
                        containerColor = addButtonColor,
                        contentColor = noteTextColor(addButtonColor),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ) {
                        AppIcon(NoteIcon.AddBlock, contentDescription = "Добавить товар", modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                .padding(padding)
                .pointerInput(note.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        val handleWidth = 40.dp.toPx()
                        if (down.position.x < size.width - (handleWidth - CardDimensions.nestedHandleHitTargetRightOverhang.toPx() + AppSpacing.screenHorizontal.toPx()) ||
                            childReorderSession != null
                        ) return@awaitEachGesture

                        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                            down.position.y >= it.offset &&
                                down.position.y < it.offset + it.size
                        } ?: return@awaitEachGesture
                        val handleHeight = 40.dp.toPx()
                        val handleTop = item.offset + (item.size - handleHeight) / 2f
                        if (down.position.y < handleTop || down.position.y > handleTop + handleHeight) {
                            return@awaitEachGesture
                        }
                        val key = item.key as? String ?: return@awaitEachGesture
                        if (!key.startsWith("nested_")) return@awaitEachGesture
                        val grabbedRow = displayedChildRows.firstOrNull { it.key == key }
                            ?: return@awaitEachGesture
                        val grabbedChild = grabbedRow.notes.firstOrNull()
                            ?: return@awaitEachGesture

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
                        if (dragStartY.isNaN() || childReorderSession != null) {
                            return@awaitEachGesture
                        }

                        val currentChildren = latestPurchaseChildren.value
                        val movingIds = if (grabbedChild.id in selectedChildIds && selectedChildIds.size > 1) {
                            selectedChildIds
                        } else {
                            setOf(grabbedChild.id)
                        }
                        if (currentChildren.size < 2 || currentChildren.none { it.id == grabbedChild.id }) {
                            return@awaitEachGesture
                        }
                        val rows = createChildDragRows(currentChildren, movingIds, grabbedChild.id)
                        val keys = rows.map { it.key }
                        childDragBaseRows = rows.associateBy { it.key }
                        val movingKey = rows.firstOrNull { row -> row.notes.any { it.id == grabbedChild.id } }?.key
                            ?: return@awaitEachGesture
                        childReorderSession = AdjacentReorderEngine.begin(keys, movingKey)
                        childDragPointerY = dragStartY
                        childDragGrabOffsetPx = dragStartY - item.offset
                        childDragReleaseRequested = false

                        var released = false
                        try {
                            var tracking = true
                            while (tracking) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: break
                                if (!change.pressed) {
                                    released = true
                                    childDragReleaseRequested = true
                                    tracking = false
                                } else {
                                    childDragPointerY = change.position.y
                                    change.consume()
                                }
                            }
                        } finally {
                            // A cancelled gesture discards the transient order.
                            if (!released && childReorderSession != null) {
                                resetChildDrag()
                            }
                        }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(if (selectedChildIds.isNotEmpty()) 0.dp else 4.dp),
            contentPadding = PaddingValues(top = AppSpacing.sm, bottom = AppSpacing.lg)
        ) {
            if (note.type == RecordType.PURCHASE && !isPurchaseListItem) {
                // Keep the first task row from becoming LazyColumn's viewport anchor
                // while adjacent purchases exchange positions.
                item(key = "purchase_reorder_viewport_anchor") {
                    Spacer(Modifier.height(1.dp))
                }
            }
            if (note.type != RecordType.NOTE &&
                (note.descriptionEnabled || isPurchaseListItem)) {
                item(key = "record_options") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screenHorizontal),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                    ) {
                        if (note.titleEnabled && isPurchaseListItem) {
                            OutlinedTextField(
                                value = note.titleText,
                                onValueChange = { onChange(note.copy(titleText = it)) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Название") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
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
                        if (isPurchaseListItem && purchaseDetailsExpanded) {
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
                }
            }
            if (note.type != RecordType.PURCHASE || isPurchaseListItem) {
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
                        .padding(horizontal = AppSpacing.screenHorizontal)
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
            }
            if (note.type == RecordType.PURCHASE && !isPurchaseListItem) {
                if (note.children.isEmpty()) {
                    item(key = "empty_nested_purchases") {
                        Text(
                            "Пока нет товаров",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screenHorizontal).padding(vertical = AppSpacing.md),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
                itemsIndexed(displayedChildRows, key = { _, row -> row.key }) { index, row ->
                    val group = row.notes.size > 1
                    NestedRecordCard(
                        selected = group || row.notes.any { it.id in selectedChildIds },
                        dark = dark,
                        selectionColor = selectionColor,
                        showDivider = index != displayedChildRows.lastIndex,
                        modifier = Modifier.fillMaxWidth()
                            .animateItemPlacement(animationSpec = tween(durationMillis = NOTE_REORDER_ANIMATION_DURATION_MILLIS))
                    ) {
                        if (group) {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .heightIn(min = CardDimensions.nestedGroupMinHeight),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                MainCardHeader(
                                    text = "Группа",
                                    textColor = MaterialTheme.colorScheme.onSurface,
                                    compressed = true
                                )
                            }
                        } else {
                            val child = row.notes.first()
                            val childStatus = child.recordStatus()
                            val childText = child.blocks.toPlainText().ifBlank { "Пустой товар" }
                            val childSelected = child.id in selectedChildIds
                            val childTextColor = if (childStatus == RecordStatus.COMPLETED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            val dragHandleColor = childTextColor
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(Color.Transparent)
                                    .padding(vertical = AppSpacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    childText,
                                    modifier = Modifier.weight(1f).pointerInput(child.id, selectedChildIds) {
                                        detectTapGestures(
                                            onTap = {
                                                if (selectedChildIds.isNotEmpty()) {
                                                    selectedChildIds = if (childSelected) selectedChildIds - child.id else selectedChildIds + child.id
                                                } else {
                                                    autoFocusChildId = null
                                                    selectedChildId = child.id
                                                }
                                            },
                                            onLongPress = { selectedChildIds = selectedChildIds + child.id }
                                        )
                                    },
                                    color = childTextColor,
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp,
                                    textDecoration = if (childStatus == RecordStatus.COMPLETED)
                                        TextDecoration.LineThrough else TextDecoration.None
                                )
                                Spacer(Modifier.width(12.dp))
                                Checkbox(
                                    checked = childStatus == RecordStatus.COMPLETED,
                                    onCheckedChange = { checked ->
                                        val changed = child.copy(
                                            status = if (checked) RecordStatus.COMPLETED else RecordStatus.WAITING,
                                            deadlineResumeOverride = false
                                        )
                                        latestPurchaseOnChange.value(
                                            withUpdatedRecordChildren(latestPurchaseNote.value,
                                                latestPurchaseChildren.value.map { if (it.id == child.id) changed else it })
                                        )
                                    },
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.width(14.dp))
                                DragHandleIcon(color = dragHandleColor)
                            }
                        }
                    }
                }
            }
        }
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
