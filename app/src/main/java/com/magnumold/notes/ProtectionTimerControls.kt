package com.magnumold.notes

import android.media.RingtoneManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Таймер защиты от переутомления", style = MaterialTheme.typography.titleSmall)
        when {
            timer != null && timer.taskId != taskId -> {
                Text("Уже запущен для: ${timer.subtaskTitle ?: timer.taskTitle}")
                Text("Осталось: ${formatProtectionDuration(ProtectionTimerEngine.remainingMillis(timer, nowMillis))}")
            }
            timer != null -> {
                Text("Осталось: ${formatProtectionDuration(ProtectionTimerEngine.remainingMillis(timer, nowMillis))}")
                Text(if (timer.endMode == ProtectionEndMode.SIGNAL_ONLY) "По окончании — только сигнал" else "По окончании — остановить учёт и поставить на паузу")
                OutlinedButton(onClick = { onStateChange(state.copy(protectionTimer = null)) }) {
                    Text("Остановить таймер")
                }
            }
            else -> {
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { value -> minutesText = value.filter { it.isDigit() }.take(4) },
                    modifier = Modifier,
                    label = { Text("Продолжительность, минут") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedMode == ProtectionEndMode.SIGNAL_ONLY,
                            onClick = { selectedMode = ProtectionEndMode.SIGNAL_ONLY }
                        )
                        Text("Только сигнал")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedMode == ProtectionEndMode.STOP_AND_PAUSE,
                            onClick = { selectedMode = ProtectionEndMode.STOP_AND_PAUSE }
                        )
                        Text("Остановить и поставить на паузу")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onChooseSound) { Text("Выбрать звук") }
                    TextButton(onClick = {
                        if (state.soundEnabled) {
                            val uri = state.soundUri?.let(android.net.Uri::parse)
                                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            RingtoneManager.getRingtone(context, uri)?.play()
                        }
                    }, enabled = state.soundEnabled) { Text("Прослушать") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = state.soundEnabled,
                        onCheckedChange = { onStateChange(state.copy(soundEnabled = it)) }
                    )
                    Text("Звуковой сигнал")
                }
                Button(
                    enabled = (minutesText.toLongOrNull() ?: 0L) > 0L,
                    onClick = {
                        val duration = (minutesText.toLongOrNull() ?: return@Button) * 60_000L
                        val newTimer = ProtectionTimerEngine.start(
                            current = state.protectionTimer,
                            taskId = taskId,
                            taskTitle = taskTitle,
                            subtaskTitle = subtaskTitle,
                            nowMillis = System.currentTimeMillis(),
                            durationMillis = duration,
                            endMode = selectedMode,
                            soundUri = state.soundUri,
                            soundEnabled = state.soundEnabled,
                            parentTaskId = parentTaskId
                        )
                        onStateChange(state.copy(protectionTimer = newTimer))
                        minutesText = ""
                    }
                ) { Text("Запустить таймер") }
            }
        }
    }
}

private fun formatProtectionDuration(durationMillis: Long): String {
    val seconds = durationMillis.coerceAtLeast(0L) / 1_000L
    return "%02d:%02d:%02d".format(seconds / 3_600L, (seconds % 3_600L) / 60L, seconds % 60L)
}
