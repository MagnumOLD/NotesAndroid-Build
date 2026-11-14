package com.magnumold.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class ChildDragRow(val notes: List<Note>, val key: String)

internal fun createChildDragRows(
    children: List<Note>,
    selectedIds: Set<Long>,
    anchorId: Long
): List<ChildDragRow> {
    val selected = children.filter { it.id in selectedIds }
    if (selected.size <= 1) return children.map { ChildDragRow(listOf(it), "nested_${it.id}") }
    val groupAnchorIndex = children.indexOfFirst { it.id == anchorId }
    return buildList {
        children.forEachIndexed { index, child ->
            if (index == groupAnchorIndex) add(ChildDragRow(selected, "nested_group"))
            if (child.id !in selectedIds) add(ChildDragRow(listOf(child), "nested_${child.id}"))
        }
    }
}
internal fun formatRecordDate(timestamp: Long): String =
    java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.format(java.util.Date(timestamp))


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ParentRecordHeader(
    note: Note,
    placeholder: String,
    topBarColor: Color,
    autoFocus: Boolean,
    onAutoFocusConsumed: () -> Unit,
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onDelete: () -> Unit,
    onOpenStatistics: () -> Unit,
    onShare: () -> Unit,
    onChange: (Note) -> Unit
) {
    var actionsMenuExpanded by remember(note.id) { mutableStateOf(false) }
    val foreground = noteTextColor(topBarColor)
    val completed = note.recordStatus() == RecordStatus.COMPLETED
    val statusTint = if (completed) Color(0xFF83A98C) else Color(0xFF8E8E93)
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember(note.id) { FocusRequester() }
    var fieldLaidOut by remember(note.id) { mutableStateOf(false) }

    LaunchedEffect(note.id, autoFocus, fieldLaidOut) {
        if (autoFocus && fieldLaidOut) {
            withFrameNanos { }
            focusRequester.requestFocus()
            withFrameNanos { }
            keyboardController?.show()
            onAutoFocusConsumed()
        }
    }

    AppPageTopBar(
        backgroundColor = topBarColor,
        title = {
            if (selectedCount > 0) {
                Text(
                    "$selectedCount выбрано",
                    style = AppTypography.bodyStyle(), fontWeight = FontWeight.Medium,
                    color = foreground
                )
            } else {
                BasicTextField(
                    value = note.titleText,
                    onValueChange = { onChange(note.copy(titleText = it)) },
                    modifier = Modifier.fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onGloballyPositioned { fieldLaidOut = true },
                    singleLine = true,
                    textStyle = AppTypography.editorTitleStyle().copy(
                        color = foreground,
                        textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(foreground),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (note.titleText.isEmpty()) {
                                Text(
                                    placeholder,
                                    color = foreground.copy(alpha = 0.48f),
                                    style = AppTypography.editorTitleStyle()
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        },
        actions = {
            if (selectedCount > 0) {
                IconButton(onClick = onClearSelection) {
                    AppIcon(NoteIcon.Close, "Отменить выделение", tint = foreground)
                }
            } else {
                IconButton(
                    onClick = {
                        val nextStatus = if (completed) RecordStatus.WAITING else RecordStatus.COMPLETED
                        val resumingMissed = note.recordStatus() == RecordStatus.MISSED &&
                            nextStatus != RecordStatus.MISSED
                        onChange(note.copy(
                            status = nextStatus,
                            statusBeforeAutoCompletion = null,
                            deadlineResumeOverride = resumingMissed
                        ))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = if (completed) "Выполнено" else "Не выполнено",
                        tint = statusTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Box {
                    IconButton(onClick = { actionsMenuExpanded = true }) {
                        AppIcon(NoteIcon.More, "Действия", tint = foreground)
                    }
                    DropdownMenu(
                        expanded = actionsMenuExpanded,
                        onDismissRequest = { actionsMenuExpanded = false }
                    ) {
                        if (note.type == RecordType.TASK) {
                            DropdownMenuItem(
                                text = { Text("Статистика") },
                                onClick = {
                                    actionsMenuExpanded = false
                                    onOpenStatistics()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Поделиться") },
                                onClick = {
                                    actionsMenuExpanded = false
                                    onShare()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Удалить") },
                            onClick = {
                                actionsMenuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
internal fun RecordEditor(
    note: Note, dark: Boolean, topBarColor: Color, addButtonColor: Color, selectionColor: Color, autoFocus: Boolean,
    onBack: () -> Unit, onChange: (Note) -> Unit, onDelete: (Boolean) -> Unit, onShare: () -> Unit
) {
    if (note.type == RecordType.PURCHASE) {
        PurchaseEditor(note, dark, topBarColor, addButtonColor, selectionColor, autoFocus,
            onBack = onBack, onChange = onChange, onDelete = onDelete, onShare = onShare)
    } else {
        NoteEditor(note, dark, topBarColor, addButtonColor, selectionColor, autoFocus,
            onBack = onBack, onChange = onChange, onDelete = onDelete, onShare = onShare)
    }
}

@Composable
internal fun ChecklistMark(checked: Boolean, onClick: () -> Unit) {
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
