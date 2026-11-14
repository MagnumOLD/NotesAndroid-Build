package com.magnumold.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Actual work time for one subtask. Every subtask is tracked automatically. */
@Composable
internal fun WorkTimeControls(
    taskId: Long,
    state: TimeTrackingState,
    status: RecordStatus,
    onStatusChange: (RecordStatus) -> Unit,
    onAddManualSession: () -> Unit
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
        title = "Фактическое время",
        headerAction = {
            IconButton(onClick = onAddManualSession) {
                Icon(Icons.Outlined.Add, contentDescription = "Добавить время")
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Всего", style = AppTypography.compactBodyStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatWorkDuration(totalMillis),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            when (status) {
                RecordStatus.IN_PROGRESS -> {
                    TextButton(onClick = { onStatusChange(RecordStatus.PAUSED) }) { Text("Пауза") }
                    TextButton(onClick = { onStatusChange(RecordStatus.COMPLETED) }) { Text("Завершить") }
                }
                RecordStatus.PAUSED -> {
                    TextButton(onClick = { onStatusChange(RecordStatus.IN_PROGRESS) }) { Text("Продолжить") }
                    TextButton(onClick = { onStatusChange(RecordStatus.COMPLETED) }) { Text("Завершить") }
                }
                RecordStatus.COMPLETED -> {
                    TextButton(onClick = { onStatusChange(RecordStatus.IN_PROGRESS) }) { Text("Возобновить работу") }
                }
                RecordStatus.CANCELLED -> {
                    Text("Отменена", style = AppTypography.compactBodyStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RecordStatus.MISSED -> {
                    Text("Просрочена", style = AppTypography.compactBodyStyle(), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onStatusChange(RecordStatus.IN_PROGRESS) }) { Text("Начать") }
                }
                else -> {
                    TextButton(onClick = { onStatusChange(RecordStatus.IN_PROGRESS) }) { Text("Начать работу") }
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
    TimeTrackingSection(title = "Фактическое время") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Всего по подзадачам", style = AppTypography.compactBodyStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                formatWorkDuration(totalMillis),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatWorkDuration(value: Long): String {
    val seconds = (value / 1_000L).coerceAtLeast(0L)
    val hours = seconds / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    val remainingSeconds = seconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
    else "%02d:%02d".format(minutes, remainingSeconds)
}
