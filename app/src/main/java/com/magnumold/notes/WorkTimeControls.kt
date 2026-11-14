package com.magnumold.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

/** Work controls are status-driven: the status synchronizer owns session start/stop. */
@Composable
internal fun WorkTimeControls(
    taskId: Long,
    state: TimeTrackingState,
    status: RecordStatus,
    onStatusChange: (RecordStatus) -> Unit,
    onOpenHistory: () -> Unit
) {
    var nowMillis by remember(taskId) { mutableLongStateOf(System.currentTimeMillis()) }
    val active = state.activeSessions[taskId]

    LaunchedEffect(taskId, active?.startedAtMillis) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val totalMillis = TimeTrackingEngine.taskDurationMillis(state, taskId, nowMillis)
    val hasHistory = state.sessions.any { it.taskId == taskId }

    fun durationLabel(value: Long): String {
        val seconds = (value / 1_000L).coerceAtLeast(0L)
        val hours = seconds / 3_600L
        val minutes = (seconds % 3_600L) / 60L
        val remainingSeconds = seconds % 60L
        return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
        else "%02d:%02d".format(minutes, remainingSeconds)
    }

    TimeTrackingSection(title = "Учёт времени") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
        ) {
            Text(
                "Всего: ${durationLabel(totalMillis)}",
                modifier = Modifier.weight(1f),
                style = AppTypography.compactBodyStyle(),
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onOpenHistory, enabled = hasHistory) { Text("История") }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
        ) {
            when (status) {
                RecordStatus.IN_PROGRESS -> {
                    Text(
                        "Выполняется",
                        modifier = Modifier.weight(1f),
                        style = AppTypography.compactBodyStyle(),
                        color = Color(0xFF4CAF50)
                    )
                    TextButton(onClick = { onStatusChange(RecordStatus.PAUSED) }) { Text("Остановить") }
                    TextButton(onClick = { onStatusChange(RecordStatus.COMPLETED) }) { Text("Завершить") }
                }
                RecordStatus.PAUSED -> {
                    Text(
                        "Остановлена",
                        modifier = Modifier.weight(1f),
                        style = AppTypography.compactBodyStyle(),
                        color = Color(0xFFFF9800)
                    )
                    TextButton(onClick = { onStatusChange(RecordStatus.IN_PROGRESS) }) { Text("Продолжить работу") }
                    TextButton(onClick = { onStatusChange(RecordStatus.COMPLETED) }) { Text("Завершить") }
                }
                RecordStatus.COMPLETED -> {
                    Text(
                        "Завершена",
                        modifier = Modifier.weight(1f),
                        style = AppTypography.compactBodyStyle(),
                        color = Color(0xFF2196F3)
                    )
                    TextButton(onClick = { onStatusChange(RecordStatus.IN_PROGRESS) }) { Text("Возобновить работу") }
                }
                else -> {
                    Text(
                        "Ожидает",
                        modifier = Modifier.weight(1f),
                        style = AppTypography.compactBodyStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { onStatusChange(RecordStatus.IN_PROGRESS) }) { Text("Начать работу") }
                }
            }
        }
    }
}
