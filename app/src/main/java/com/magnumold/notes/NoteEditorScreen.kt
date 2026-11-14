package com.magnumold.notes

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
internal fun NoteEditor(
    note: Note, dark: Boolean, topBarColor: Color, addButtonColor: Color, selectionColor: Color, autoFocus: Boolean,
    isTaskListItem: Boolean = false,
    parentTaskId: Long? = null,
    parentTaskTitle: String? = null,
    timeTrackingState: TimeTrackingState = TimeTrackingState(),
    onTimeTrackingStateChange: (TimeTrackingState) -> Unit = {},
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
    var taskDetailsExpanded by remember(note.id) { mutableStateOf(false) }
    var timeAnalyticsVisible by remember(note.id) { mutableStateOf(false) }
    var statusMenuExpanded by remember(note.id) { mutableStateOf(false) }
    var dateModeMenuExpanded by remember(note.id) { mutableStateOf(false) }
    var datePickerVisible by remember(note.id) { mutableStateOf(false) }
    var pendingDateMode by remember(note.id) { mutableStateOf(note.dateMode ?: RecordDateMode.DATE) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = note.dateAt)
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pickedUri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        val soundUri = pickedUri?.toString()
        val soundEnabled = pickedUri != null
        onTimeTrackingStateChange(
            timeTrackingState.copy(
                soundUri = soundUri,
                soundEnabled = soundEnabled,
                protectionTimer = timeTrackingState.protectionTimer?.copy(
                    soundUri = soundUri,
                    soundEnabled = soundEnabled
                )
            )
        )
    }
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
    val latestTaskNote = rememberUpdatedState(note)
    val latestTaskChildren = rememberUpdatedState(note.children)
    val latestTaskOnChange = rememberUpdatedState(onChange)
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
        if (note.type == RecordType.TASK) {
            val effectiveStatus = note.recordStatus()
            if (effectiveStatus != note.status) onChange(note.copy(status = effectiveStatus))
        }
    }

    val selectedChild = note.children.firstOrNull { it.id == selectedChildId }
    if (selectedChild != null) {
        NoteEditor(
            note = selectedChild, dark = dark, topBarColor = topBarColor,
            addButtonColor = addButtonColor, selectionColor = selectionColor,
            autoFocus = selectedChild.id == autoFocusChildId, isTaskListItem = true,
            parentTaskId = note.id,
            parentTaskTitle = note.titleText.takeIf { note.titleEnabled && it.isNotBlank() }
                ?: note.blocks.toPlainText().lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "Задача" },
            timeTrackingState = timeTrackingState,
            onTimeTrackingStateChange = onTimeTrackingStateChange,
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

    if (timeAnalyticsVisible) {
        val currentTitle = note.titleText.takeIf { note.titleEnabled && it.isNotBlank() }
            ?: note.blocks.toPlainText().lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "Задача" }
        TimeTrackingAnalyticsScreen(
            state = timeTrackingState,
            onStateChange = onTimeTrackingStateChange,
            onBack = { timeAnalyticsVisible = false },
            manualTaskId = if (isTaskListItem) note.id else null,
            manualParentTaskId = parentTaskId,
            manualTaskTitle = if (isTaskListItem) parentTaskTitle ?: "Задача" else currentTitle,
            manualSubtaskTitle = if (isTaskListItem) currentTitle else null
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
            val currentChildren = latestTaskChildren.value
            val reordered = session.keys.flatMap { childDragBaseRows[it]?.notes.orEmpty() }
            if (reordered.size == currentChildren.size &&
                reordered.map { it.id }.toSet() == currentChildren.map { it.id }.toSet()
            ) {
                latestTaskOnChange.value.invoke(
                    withUpdatedRecordChildren(latestTaskNote.value, reordered)
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

    AppPageScaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (note.type == RecordType.TASK && !isTaskListItem) {
                ParentRecordHeader(
                    note = note,
                    placeholder = "Название задачи",
                    topBarColor = topBarColor,
                    autoFocus = autoFocus && !parentAutoFocusConsumed,
                    onAutoFocusConsumed = { parentAutoFocusConsumed = true },
                    selectedCount = selectedChildIds.size,
                    onClearSelection = { selectedChildIds = emptySet() },
                    onDelete = { onDelete(false) },
                    onOpenStatistics = { timeAnalyticsVisible = true },
                    onShare = onShare,
                    onChange = onChange
                )
            } else {
                AppEditorTopBar(
                    title = if (isTaskListItem) "Подзадача" else "Заметка",
                    actions = {
                        if (isTaskListItem) {
                            IconButton(onClick = { taskDetailsExpanded = !taskDetailsExpanded }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = if (taskDetailsExpanded) "Скрыть дату и статус" else "Показать дату и статус")
                            }
                        }
                        if (!isTaskListItem && note.type != RecordType.NOTE) {
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
                        if (!isTaskListItem) IconButton(onClick = onShare) { AppIcon(NoteIcon.Share, "Поделиться") }
                    },
                    backgroundColor = topBarColor
                )
            }
        },
        floatingActionButton = {
            if (note.type == RecordType.TASK && !isTaskListItem) {
                Box(modifier = Modifier.offset(y = PrimaryAddButtonDimensions.verticalOffset)) {
                    FloatingActionButton(
                        modifier = Modifier.size(PrimaryAddButtonDimensions.diameter),
                        onClick = { createChild(RecordType.TASK) },
                        containerColor = addButtonColor,
                        contentColor = noteTextColor(addButtonColor),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ) {
                        AppIcon(NoteIcon.AddBlock, contentDescription = "Добавить задачу", modifier = Modifier.size(28.dp))
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
                        val handleWidth = AppRowDimensions.controlTouchTarget.toPx()
                        if (down.position.x < size.width - (handleWidth - CardDimensions.nestedHandleHitTargetRightOverhang.toPx() + AppSpacing.screenHorizontal.toPx()) ||
                            childReorderSession != null
                        ) return@awaitEachGesture

                        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                            down.position.y >= it.offset &&
                                down.position.y < it.offset + it.size
                        } ?: return@awaitEachGesture
                        val handleHeight = AppRowDimensions.controlTouchTarget.toPx()
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

                        val currentChildren = latestTaskChildren.value
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
            verticalArrangement = Arrangement.spacedBy(AppPageLayout.editorListItemSpacing),
            contentPadding = AppPageLayout.editorListContentPadding
        ) {
            if (note.type == RecordType.TASK && !isTaskListItem) {
                // Keep the first purchase row from becoming LazyColumn's viewport anchor
                // while adjacent tasks exchange positions.
                item(key = "task_reorder_viewport_anchor") {
                    Spacer(Modifier.height(1.dp))
                }
            }
            if (note.type == RecordType.TASK) {
                if (isTaskListItem) {
                    item(key = "work_time_controls") {
                        val currentTitle = note.titleText.takeIf { note.titleEnabled && it.isNotBlank() }
                            ?: note.blocks.toPlainText().lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "Задача" }
                        WorkTimeControls(
                            taskId = note.id,
                            state = timeTrackingState,
                            status = note.recordStatus(),
                            onStatusChange = { nextStatus ->
                                val resumingMissed = note.recordStatus() == RecordStatus.MISSED &&
                                    nextStatus != RecordStatus.MISSED
                                onChange(note.copy(
                                    status = nextStatus,
                                    deadlineResumeOverride = resumingMissed
                                ))
                            },
                            onOpenHistory = { timeAnalyticsVisible = true }
                        )
                    }
                }
                if (isTaskListItem) {
                    item(key = "protection_timer") {
                        val currentTitle = note.titleText.takeIf { note.titleEnabled && it.isNotBlank() }
                            ?: note.blocks.toPlainText().lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "Задача" }
                        ProtectionTimerControls(
                            taskId = note.id,
                            taskTitle = parentTaskTitle ?: "Задача",
                            subtaskTitle = currentTitle,
                            parentTaskId = parentTaskId,
                            state = timeTrackingState,
                            onStateChange = onTimeTrackingStateChange,
                            onChooseSound = {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Выберите сигнал")
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                    putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                        timeTrackingState.soundUri?.let(Uri::parse)
                                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                    )
                                }
                                ringtonePicker.launch(intent)
                            }
                        )
                    }
                }
            }
            if (note.type == RecordType.TASK && !isTaskListItem) {
                item(key = "parent_work_time_summary") {
                    ParentWorkTimeSummary(
                        parentTaskId = note.id,
                        state = timeTrackingState,
                        onOpenHistory = { timeAnalyticsVisible = true }
                    )
                }
            }
            if (note.type != RecordType.NOTE &&
                (note.descriptionEnabled || isTaskListItem)) {
                item(key = "record_options") {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .appPageHorizontalInsets(),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        if (note.titleEnabled && isTaskListItem) {
                            BasicTextField(
                                value = note.titleText,
                                onValueChange = { onChange(note.copy(titleText = it)) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = AppEditorDimensions.taskTitleMinHeight),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                textStyle = AppTypography.screenTitleStyle().copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (note.titleText.isEmpty()) {
                                            Text(
                                                "Название",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                                style = AppTypography.screenTitleStyle()
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                        }
                        if (note.descriptionEnabled) {
                            BasicTextField(
                                value = note.descriptionText,
                                onValueChange = { onChange(note.copy(descriptionText = it)) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = AppEditorDimensions.descriptionMinHeight),
                                textStyle = AppTypography.bodyStyle().copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface),
                                decorationBox = { inner ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth().heightIn(min = AppEditorDimensions.descriptionMinHeight),
                                        contentAlignment = Alignment.TopStart
                                    ) {
                                        if (note.descriptionText.isEmpty()) {
                                            Text(
                                                "Описание",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                                style = AppTypography.bodyStyle()
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                        }
                        if (isTaskListItem && taskDetailsExpanded) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Дата", color = MaterialTheme.colorScheme.onSurfaceVariant, style = AppTypography.sectionLabelStyle())
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
                if (isTaskListItem && taskDetailsExpanded) {
                    item(key = "record_status") {
                        Row(
                            modifier = Modifier.fillMaxWidth().appPageHorizontalInsets().padding(vertical = AppSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Статус", color = MaterialTheme.colorScheme.onSurfaceVariant, style = AppTypography.sectionLabelStyle())
                            Spacer(Modifier.weight(1f))
                            Text(
                                recordStatusText(note.recordStatus()),
                                color = when (note.recordStatus()) {
                                    RecordStatus.IN_PROGRESS -> Color(0xFF4CAF50)
                                    RecordStatus.PAUSED -> Color(0xFFFF9800)
                                    RecordStatus.COMPLETED -> Color(0xFF2196F3)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
            if (note.type != RecordType.TASK || isTaskListItem) {
                item(key = "record_body") {
                val bodyFocusRequester = remember(note.id) { FocusRequester() }
                LaunchedEffect(note.id, autoFocus) {
                    if (autoFocus) {
                        bodyFocusRequester.requestFocus()
                        keyboardController?.show()
                    }
                }
                val editorTextStyle = AppTypography.bodyStyle().copy(color = MaterialTheme.colorScheme.onSurface)
                BasicTextField(
                    value = fieldValue,
                    onValueChange = {
                        fieldValue = it
                        updateText(it.text)
                    },
                    modifier = Modifier.fillMaxWidth()
                        .appPageHorizontalInsets()
                        .focusRequester(bodyFocusRequester)
                        .heightIn(min = AppEditorDimensions.bodyMinHeight)
                        .onFocusChanged { state ->
                            if (!state.isFocused && editingBlockId == editorBlockId) editingBlockId = null
                        },
                    textStyle = editorTextStyle,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(min = AppEditorDimensions.bodyMinHeight),
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
            if (note.type == RecordType.TASK && !isTaskListItem) {
                if (note.children.isEmpty()) {
                    item(key = "empty_nested_tasks") {
                        Text(
                            "Пока нет задач",
                            modifier = Modifier.fillMaxWidth().appPageHorizontalInsets().padding(vertical = AppSpacing.md),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = AppTypography.sectionLabelStyle()
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
                                    .heightIn(min = CardDimensions.nestedRowMinHeight),
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
                            val childText = child.blocks.toPlainText().ifBlank { "Пустая задача" }
                            val childSelected = child.id in selectedChildIds
                            val childTextColor = if (childStatus == RecordStatus.COMPLETED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            val dragHandleColor = childTextColor
                            NestedRecordRow {
                                TaskStatusButton(
                                    status = childStatus,
                                    onClick = { /* Status is controlled from the subtask work controls. */ }
                                )
                                Spacer(Modifier.width(AppRowDimensions.taskStatusToTextGap))
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
                                    style = AppTypography.cardPreviewStyle(),
                                    textDecoration = if (childStatus == RecordStatus.COMPLETED)
                                        TextDecoration.LineThrough else TextDecoration.None
                                )
                                Spacer(Modifier.width(AppRowDimensions.taskTextToHandleGap))
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
