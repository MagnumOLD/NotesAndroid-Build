package com.magnumold.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** Compact actual-time controls. Task status is deliberately not modified here. */
@Composable
internal fun WorkTimeControls(
    taskId: Long,
    parentTaskId: Long?,
    taskTitle: String,
    subtaskTitle: String?,
    state: TimeTrackingState,
    allowTracking: Boolean,
    trackingEnabled: Boolean = false,
    onTrackingEnabledChange: (Boolean) -> Unit = {},
    onStateChange: (TimeTrackingState) -> Unit
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val active = state.activeSessions[taskId]
    val totalMillis = if (allowTracking) {
        TimeTrackingEngine.taskDurationMillis(state, taskId, nowMillis)
    } else {
        TimeTrackingEngine.parentDurationMillis(state, taskId, nowMillis)
    }
    val hasHistory = state.sessions.any { it.taskId == taskId }
    val durationText = formatWorkDuration(totalMillis)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Фактическое время")
        if (allowTracking) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = trackingEnabled, onCheckedChange = onTrackingEnabledChange)
                Text("Учитывать время")
            }
        }
        Text(durationText)
        if (allowTracking && trackingEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (active == null) {
                    Button(onClick = {
                        onStateChange(
                            TimeTrackingEngine.start(
                                state = state,
                                taskId = taskId,
                                parentTaskId = parentTaskId,
                                taskTitle = taskTitle,
                                subtaskTitle = subtaskTitle,
                                nowMillis = System.currentTimeMillis()
                            )
                        )
                    }) {
                        Text(if (hasHistory) "Продолжить учёт" else "Начать учёт")
                    }
                } else {
                    Button(onClick = {
                        onStateChange(
                            TimeTrackingEngine.finish(
                                state, taskId, System.currentTimeMillis(), WorkSessionEndReason.PAUSE
                            )
                        )
                    }) { Text("Пауза") }
                    OutlinedButton(onClick = {
                        onStateChange(
                            TimeTrackingEngine.finish(
                                state, taskId, System.currentTimeMillis(), WorkSessionEndReason.STOP
                            )
                        )
                    }) { Text("Стоп") }
                }
            }
        }
    }
}

private fun formatWorkDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
