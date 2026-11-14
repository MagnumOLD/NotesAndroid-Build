package com.magnumold.notes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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

    TimeTrackingSection(title = "Фактическое время") {
        if (allowTracking) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = trackingEnabled, onCheckedChange = onTrackingEnabledChange)
                Text("Учитывать время", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                durationText,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (allowTracking && trackingEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                    if (active == null) {
                        TextButton(onClick = {
                            onStateChange(TimeTrackingEngine.start(
                                state = state, taskId = taskId, parentTaskId = parentTaskId,
                                taskTitle = taskTitle, subtaskTitle = subtaskTitle,
                                nowMillis = System.currentTimeMillis()
                            ))
                        }) { Text(if (hasHistory) "Продолжить" else "Начать") }
                    } else {
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
                    }
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
