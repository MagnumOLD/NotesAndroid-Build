package com.magnumold.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Actual work time for one subtask. Every subtask is tracked automatically. */
@Composable
internal fun WorkTimeControls(
    taskId: Long,
    state: TimeTrackingState,
    status: RecordStatus,
    onStatusChange: (RecordStatus) -> Unit
) {
    var nowMillis by remember(taskId) { mutableLongStateOf(System.currentTimeMillis()) }
    val active = state.activeSessions[taskId]

    LaunchedEffect(taskId, active?.startedAtMillis) {
        if (active == null) {
            nowMillis = System.currentTimeMillis()
        } else {
            while (true) {
                nowMillis = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }

    val totalMillis = TimeTrackingEngine.taskDurationMillis(state, taskId, nowMillis)
    TimeTrackingSection(
        title = "Время работы",
        trailingContent = {
            Text(
                formatWorkDuration(totalMillis),
                style = AppTypography.emphasisBodyStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        contentSpacing = AppSpacing.sm
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(vertical = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs, Alignment.End)
        ) {
            when (status) {
                RecordStatus.IN_PROGRESS -> {
                    TextButton(onClick = { onStatusChange(RecordStatus.PAUSED) }) { Text("Пауза", style = AppTypography.actionStyle()) }
                    TextButton(onClick = { onStatusChange(RecordStatus.COMPLETED) }) { Text("Завершить", style = AppTypography.actionStyle()) }
                }
                RecordStatus.PAUSED -> {
                    TextButton(onClick = { onStatusChange(RecordStatus.IN_PROGRESS) }) { Text("Продолжить", style = AppTypography.actionStyle()) }
                    TextButton(onClick = { onStatusChange(RecordStatus.COMPLETED) }) { Text("Завершить", style = AppTypography.actionStyle()) }
                }
                RecordStatus.COMPLETED -> {
                    TextButton(onClick = { onStatusChange(RecordStatus.IN_PROGRESS) }) { Text("Возобновить работу", style = AppTypography.actionStyle()) }
                }
                RecordStatus.CANCELLED -> {
                    Text("Отменена", style = AppTypography.compactBodyStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RecordStatus.MISSED -> {
                    Text("Просрочена", style = AppTypography.compactBodyStyle(), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onStatusChange(RecordStatus.IN_PROGRESS) }) { Text("Начать", style = AppTypography.actionStyle()) }
                }
                else -> {
                    TextButton(onClick = { onStatusChange(RecordStatus.IN_PROGRESS) }) { Text("Начать работу", style = AppTypography.actionStyle()) }
                }
            }
        }
    }
}

/** Parent tasks show only the sum of their subtasks; they never run their own timer. */
@Composable
internal fun ParentWorkTimeSummary(
    parentTaskId: Long,
    state: TimeTrackingState
) {
    var nowMillis by remember(parentTaskId) { mutableLongStateOf(System.currentTimeMillis()) }
    val activeSignature = state.activeSessions.values
        .filter { it.parentTaskId == parentTaskId }
        .map { it.taskId to it.startedAtMillis }

    LaunchedEffect(parentTaskId, activeSignature) {
        if (activeSignature.isEmpty()) {
            nowMillis = System.currentTimeMillis()
        } else {
            while (true) {
                nowMillis = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }

    val totalMillis = TimeTrackingEngine.parentDurationMillis(state, parentTaskId, nowMillis)
    TimeTrackingSection(
        title = "Затраты времени",
        trailingContent = {
            Text(
                formatWorkDuration(totalMillis),
                style = AppTypography.emphasisBodyStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

private fun formatWorkDuration(value: Long): String {
    val seconds = (value / 1_000L).coerceAtLeast(0L)
    val hours = seconds / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    val remainingSeconds = seconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
    else "%02d:%02d".format(minutes, remainingSeconds)
}
