package com.magnumold.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Compact manual controls; every subtask is tracked automatically from status changes. */
@Composable
internal fun WorkTimeControls(
    taskId: Long,
    parentTaskId: Long?,
    taskTitle: String,
    subtaskTitle: String?,
    state: TimeTrackingState,
    onStateChange: (TimeTrackingState) -> Unit
) {
    val active = state.activeSessions[taskId]
    val hasHistory = state.sessions.any { it.taskId == taskId }

    TimeTrackingSection(title = "Учёт времени") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
        ) {
            Text(
                if (active != null) "Идёт учёт времени" else if (hasHistory) "Учёт приостановлен" else "Запустится при начале работы",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (active != null) {
                TextButton(onClick = {
                    onStateChange(TimeTrackingEngine.finish(
                        state, taskId, System.currentTimeMillis(), WorkSessionEndReason.PAUSE
                    ))
                }) { Text("Пауза") }
                TextButton(onClick = {
                    onStateChange(TimeTrackingEngine.finish(
                        state, taskId, System.currentTimeMillis(), WorkSessionEndReason.STOP
                    ))
                }) { Text("Стоп") }
            } else {
                TextButton(onClick = {
                    onStateChange(TimeTrackingEngine.start(
                        state = state, taskId = taskId, parentTaskId = parentTaskId,
                        taskTitle = taskTitle, subtaskTitle = subtaskTitle,
                        nowMillis = System.currentTimeMillis()
                    ))
                }) { Text(if (hasHistory) "Продолжить" else "Начать") }
            }
        }
    }
}
