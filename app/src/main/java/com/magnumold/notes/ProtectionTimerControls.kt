package com.magnumold.notes

import android.app.AlarmManager
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
internal fun ProtectionTimerControls(
    taskId: Long,
    taskTitle: String,
    subtaskTitle: String,
    parentTaskId: Long?,
    state: TimeTrackingState,
    onStateChange: (TimeTrackingState) -> Unit,
    onChooseSound: () -> Unit
) {
    var minutesText by remember(taskId) { mutableStateOf("") }
    var selectedMode by remember(taskId) { mutableStateOf(ProtectionEndMode.SIGNAL_ONLY) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current
    val timer = state.protectionTimer

    LaunchedEffect(timer?.endsAtMillis) {
        while (timer != null) {
            nowMillis = System.currentTimeMillis()
            if (nowMillis >= timer.endsAtMillis) break
            delay(1_000L)
        }
    }

    TimeTrackingSection(title = "Таймер защиты") {
        when {
            timer != null && timer.taskId != taskId -> {
                Text("Уже запущен: ${timer.subtaskTitle ?: timer.taskTitle}", style = MaterialTheme.typography.bodyMedium)
                Text("Осталось ${formatProtectionDuration(ProtectionTimerEngine.remainingMillis(timer, nowMillis))}", style = MaterialTheme.typography.bodyMedium)
            }
            timer != null -> {
                Row(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Осталось", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatProtectionDuration(ProtectionTimerEngine.remainingMillis(timer, nowMillis)), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    TextButton(onClick = { onStateChange(state.copy(protectionTimer = null)) }) { Text("Остановить") }
                }
                Text(
                    if (timer.endMode == ProtectionEndMode.SIGNAL_ONLY) "По окончании — только сигнал"
                    else "По окончании — остановить учёт и поставить на паузу",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Длительность, мин", style = MaterialTheme.typography.bodyMedium)
                    BasicTextField(
                        value = minutesText,
                        onValueChange = { value -> minutesText = value.filter { it.isDigit() }.take(4) },
                        modifier = androidx.compose.ui.Modifier.width(72.dp).padding(start = AppSpacing.sm),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                        decorationBox = { innerTextField ->
                            androidx.compose.foundation.layout.Box {
                                if (minutesText.isEmpty()) Text("25", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                innerTextField()
                            }
                        }
                    )
                    TextButton(
                        enabled = (minutesText.toLongOrNull() ?: 0L) > 0L,
                        onClick = {
                            val duration = (minutesText.toLongOrNull() ?: return@TextButton) * 60_000L
                            val newTimer = ProtectionTimerEngine.start(
                                current = state.protectionTimer, taskId = taskId, taskTitle = taskTitle,
                                subtaskTitle = subtaskTitle, nowMillis = System.currentTimeMillis(),
                                durationMillis = duration, endMode = selectedMode,
                                soundUri = state.soundUri, soundEnabled = state.soundEnabled,
                                parentTaskId = parentTaskId
                            )
                            onStateChange(state.copy(protectionTimer = newTimer))
                            minutesText = ""
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val alarms = context.getSystemService(AlarmManager::class.java)
                                if (!alarms.canScheduleExactAlarms()) {
                                    context.startActivity(Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:${context.packageName}")
                                    ))
                                }
                            }
                        }
                    ) { Text("Запустить") }
                }
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedMode == ProtectionEndMode.SIGNAL_ONLY, onClick = { selectedMode = ProtectionEndMode.SIGNAL_ONLY })
                        Text("Только сигнал", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedMode == ProtectionEndMode.STOP_AND_PAUSE, onClick = { selectedMode = ProtectionEndMode.STOP_AND_PAUSE })
                        Text("Остановить учёт и поставить на паузу", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Row(
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            androidx.compose.material3.Checkbox(
                checked = state.soundEnabled,
                onCheckedChange = { enabled ->
                    onStateChange(state.copy(
                        soundEnabled = enabled,
                        protectionTimer = state.protectionTimer?.copy(soundEnabled = enabled)
                    ))
                }
            )
            Text("Звуковой сигнал", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onChooseSound) { Text("Выбрать") }
            TextButton(onClick = {
                if (state.soundEnabled) {
                    val uri = state.soundUri?.let(android.net.Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    RingtoneManager.getRingtone(context, uri)?.play()
                }
            }, enabled = state.soundEnabled) { Text("Проверить") }
        }
    }
}

private fun formatProtectionDuration(durationMillis: Long): String {
    val seconds = durationMillis.coerceAtLeast(0L) / 1_000L
    return "%02d:%02d:%02d".format(seconds / 3_600L, (seconds % 3_600L) / 60L, seconds % 60L)
}
