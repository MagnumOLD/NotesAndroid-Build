package com.magnumold.notes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
internal fun MainCardHeader(
    text: String,
    textColor: Color,
    pinned: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    compressed: Boolean = false
) {
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnLongPress by rememberUpdatedState(onLongPress)
    val textInteractionModifier = if (onClick != null || onLongPress != null) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { latestOnClick?.invoke() },
                onLongPress = { latestOnLongPress?.invoke() }
            )
        }
    } else {
        Modifier
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f).then(textInteractionModifier),
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
        if (pinned) {
            Spacer(Modifier.width(8.dp))
            AppIcon(
                NoteIcon.Pin,
                contentDescription = "Закреплено",
                tint = textColor.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 5.dp).size(16.dp)
            )
        }
        Spacer(Modifier.width(20.dp))
        DragHandleIcon(
            modifier = Modifier.padding(top = 6.6.dp),
            color = textColor,
            compressed = compressed
        )
    }
}

@Composable
internal fun mainCardTextColor(color: Color, isBaseColor: Boolean): Color =
    if (isBaseColor) MaterialTheme.colorScheme.onSurface
    else noteTextColor(color, MaterialTheme.colorScheme.background)

internal fun mainCardBackground(
    color: Color,
    selected: Boolean,
    isBaseColor: Boolean,
    dark: Boolean,
    selectionColor: Color?
): Color = when {
    !selected -> color
    isBaseColor -> selectionColor ?: if (dark) Color(0xFF1C2B40) else Color(0xFFEAF2FF)
    else -> lerp(color, Color(0xFF808080), if (dark) 0.07f else 0.14f)
}

/**
 * Shared card surface for main-list cards, grouped placeholders and nested rows.
 * Owns the full-width background and divider; each card supplies its content layout.
 */
@Composable
internal fun RecordCardSurface(
    background: Color,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth().background(background)) {
        content()
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                thickness = CardDimensions.dividerThickness
            )
        }
    }
}

@Composable
internal fun NestedRecordCard(
    selected: Boolean,
    dark: Boolean,
    selectionColor: Color,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val background = mainCardBackground(
        color = MaterialTheme.colorScheme.background,
        selected = selected,
        isBaseColor = true,
        dark = dark,
        selectionColor = selectionColor
    )
    RecordCardSurface(
        background = background,
        modifier = modifier,
        showDivider = showDivider
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = AppSpacing.screenHorizontal)
        ) {
            content()
        }
    }
}

@Composable
internal fun DraggedNotesGroup(
    notes: List<Note>,
    dark: Boolean,
    noteColors: List<Color>,
    customSelectionColor: Color? = null,
    anchorNoteId: Long,
    rowHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val anchorNote = notes.firstOrNull { it.id == anchorNoteId } ?: notes.first()
    val groupColor = if (anchorNote.colorIndex == 0) {
        MaterialTheme.colorScheme.background
    } else {
        noteColors[anchorNote.colorIndex - 1]
    }
    val groupTextColor = mainCardTextColor(groupColor, anchorNote.colorIndex == 0)
    // Regular notes use the Apple-inspired selection color; colored notes keep the previous muted overlay.
    val selectedBackground = mainCardBackground(
        color = groupColor,
        selected = true,
        isBaseColor = anchorNote.colorIndex == 0,
        dark = dark,
        selectionColor = customSelectionColor
    )
    RecordCardSurface(
        background = selectedBackground,
        modifier = modifier.height(rowHeight)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.TopStart
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    start = AppSpacing.screenHorizontal,
                    end = AppSpacing.screenHorizontal,
                    top = AppSpacing.md
                )
            ) {
                MainCardHeader("Группа", groupTextColor, compressed = true)
            }
        }
    }
}

@Composable
internal fun DragHandleIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
    compressed: Boolean = false
) {
    var pressed by remember { mutableStateOf(false) }
    val spacing by animateFloatAsState(
        targetValue = if (pressed || compressed) 4f else 8f,
        animationSpec = tween(durationMillis = 150),
        label = "dragHandleSpacing"
    )
    Canvas(
        modifier = modifier
            .size(width = 16.dp, height = (spacing + 0.8f).dp)
            .pointerInput(compressed) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    try {
                        var tracking = true
                        while (tracking) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) tracking = false
                        }
                    } finally {
                        pressed = false
                    }
                }
            }
    ) {
        val stroke = 0.8.dp.toPx()
        val lineWidth = size.width - stroke
        // Keep the handle in the same color family as the row text.
        // It is slightly quieter at rest and becomes clearer while pressed.
        val visibleColor = color.copy(alpha = color.alpha * if (pressed) 0.96f else 0.50f)
        val startX = stroke / 2f
        val centerY = size.height / 2f
        val halfSpacing = spacing.dp.toPx() / 2f
        drawLine(
            color = visibleColor,
            start = Offset(startX, centerY - halfSpacing),
            end = Offset(startX + lineWidth, centerY - halfSpacing),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = visibleColor,
            start = Offset(startX, centerY + halfSpacing),
            end = Offset(startX + lineWidth, centerY + halfSpacing),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

internal fun recordStatusCompactText(status: RecordStatus): String = when (status) {
    RecordStatus.WAITING -> "Ожидает"
    RecordStatus.IN_PROGRESS -> "В работе"
    RecordStatus.COMPLETED -> "Выполнено"
    RecordStatus.CANCELLED -> "Отменено"
    RecordStatus.MISSED -> "Просрочено"
}

internal fun recordStatusText(status: RecordStatus): String = when (status) {
    RecordStatus.WAITING -> "Ожидает выполнения"
    RecordStatus.IN_PROGRESS -> "В процессе"
    RecordStatus.COMPLETED -> "Выполнено"
    RecordStatus.CANCELLED -> "Отменено"
    RecordStatus.MISSED -> "Пропущено"
}


@Composable
internal fun TaskStatusMarker(status: RecordStatus) {
    val tint = when (status) {
        RecordStatus.WAITING -> MaterialTheme.colorScheme.onSurfaceVariant
        RecordStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        RecordStatus.COMPLETED -> Color(0xFF30A46C)
        RecordStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        RecordStatus.MISSED -> MaterialTheme.colorScheme.error
    }
    val icon = when (status) {
        RecordStatus.WAITING -> Icons.Outlined.Schedule
        RecordStatus.IN_PROGRESS -> Icons.Outlined.PlayCircleOutline
        RecordStatus.COMPLETED -> Icons.Outlined.CheckCircle
        RecordStatus.CANCELLED -> Icons.Outlined.Block
        RecordStatus.MISSED -> Icons.Outlined.WarningAmber
    }
    val description = when (status) {
        RecordStatus.WAITING -> "Ожидает"
        RecordStatus.IN_PROGRESS -> "В процессе"
        RecordStatus.COMPLETED -> "Выполнено"
        RecordStatus.CANCELLED -> "Отменено"
        RecordStatus.MISSED -> "Просрочено"
    }
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
internal fun NoteRow(
    note: Note, dark: Boolean, noteColors: List<Color>, customSelectionColor: Color? = null, pinned: Boolean, selected: Boolean,
    statusNow: Long, onClick: () -> Unit, onLongPress: () -> Unit,
    colorSwipeEnabled: Boolean, onChangeColor: (Int) -> Unit,
    onMeasuredHeight: (Int) -> Unit = {}
) {
    val firstLine = if ((note.type == RecordType.TASK || note.type == RecordType.PURCHASE || note.titleEnabled) && note.titleText.isNotBlank()) note.titleText.trim()
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
    val rowTextColor = mainCardTextColor(rowColor, note.colorIndex == 0)
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
    val selectedBackground = mainCardBackground(
        color = displayedColor,
        selected = selected,
        isBaseColor = note.colorIndex == 0,
        dark = dark,
        selectionColor = customSelectionColor
    )
    val metadata = if (note.type == RecordType.NOTE) "Заметка" else recordStatusCompactText(note.recordStatus(statusNow))

    RecordCardSurface(
        background = selectedBackground,
        modifier = Modifier.fillMaxWidth()
            .onSizeChanged { onMeasuredHeight(it.height) }
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = CardDimensions.contentHeight)
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
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    start = AppSpacing.screenHorizontal,
                    end = AppSpacing.screenHorizontal,
                    top = AppSpacing.md,
                    bottom = AppSpacing.md
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MainCardHeader(
                    text = firstLine,
                    textColor = rowTextColor,
                    pinned = pinned,
                    onClick = onClick,
                    onLongPress = onLongPress
                )
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .pointerInput(note.id, selected) {
                            detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() })
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (note.type != RecordType.NOTE) {
                        Text(
                            metadata,
                            fontSize = 12.sp,
                            color = rowTextColor.copy(alpha = 0.82f),
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatNoteCreationDate(note.created),
                        fontSize = 12.sp,
                        color = rowTextColor.copy(alpha = 0.62f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

internal enum class NoteIcon {
    Back, Add, AddBlock, Search, Close, More, Palette, Archive, Trash,
    Upload, Download, Pin, Folder, Undo, Redo, Share
}

/**
 * Классические минималистичные контурные иконки Material.
 * Используется стандартная сетка Material 24×24.
 */
@Composable
internal fun AppIcon(
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
