package com.magnumold.notes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

@Composable
internal fun TaskTimerControls(taskId: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember(context) { TaskTimerStore(context) }
    var settings by remember { mutableStateOf(store.preferences()) }
    var active by remember { mutableStateOf(store.activeTimer()) }
    var totalMillis by remember { mutableStateOf(store.totalMillis(taskId)) }
    var limitText by remember(settings.defaultLimitMinutes) { mutableStateOf(settings.defaultLimitMinutes.toString()) }
    var modeMenu by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || Build.VERSION.SDK_INT < 33) startTimer(context, taskId, limitText, settings.defaultLimitMinutes)
        pendingStart = false
    }

    LaunchedEffect(taskId) {
        while (true) {
            active = store.activeTimer()
            totalMillis = store.totalMillis(taskId)
            delay(1_000L)
        }
    }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Время", style = MaterialTheme.typography.titleSmall)
            Text("Затрачено: ${formatTrackedTime(totalMillis)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)

            val isThisTaskActive = active?.taskId == taskId && active?.breakUntilMillis == null
            val isThisTaskOnBreak = active?.taskId == taskId && active?.breakUntilMillis != null
            val anotherTaskActive = active != null && active?.taskId != taskId
            if (isThisTaskActive) {
                Text("Таймер: ${formatTrackedTime(active!!.remainingMillis(System.currentTimeMillis()))} осталось",
                    style = MaterialTheme.typography.bodyMedium)
            } else if (active?.isOnBreak(System.currentTimeMillis()) == true) {
                Text("Сейчас идёт перерыв: ${formatTrackedTime(active!!.remainingMillis(System.currentTimeMillis()))}",
                    color = MaterialTheme.colorScheme.primary)
            } else if (anotherTaskActive) {
                Text("Уже запущен таймер другой подзадачи",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Лимит, мин") },
                    singleLine = true
                )
                Button(
                    enabled = !anotherTaskActive && (isThisTaskActive || isThisTaskOnBreak || active == null),
                    onClick = {
                        if (isThisTaskActive || isThisTaskOnBreak) {
                            context.startService(Intent(context, TaskTimerService::class.java)
                                .setAction(ACTION_TASK_TIMER_STOP))
                        } else if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            pendingStart = true
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            startTimer(context, taskId, limitText, settings.defaultLimitMinutes)
                        }
                    }
                ) { Text(if (isThisTaskActive || isThisTaskOnBreak) "Остановить" else "Старт") }
            }

            if (Build.VERSION.SDK_INT >= 31 &&
                !context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()
            ) {
                Text("Без точного доступа Android может задержать сигнал таймера.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = android.net.Uri.parse("package:" + context.packageName)
                    })
                }) { Text("Разрешить точные сигналы") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("После сигнала", modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { modeMenu = true }) { Text(timerModeLabel(settings.endMode)) }
                DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                    TimerEndMode.values().forEach { mode ->
                        DropdownMenuItem(text = { Text(timerModeLabel(mode)) }, onClick = {
                            settings = settings.copy(endMode = mode)
                            store.savePreferences(settings)
                            modeMenu = false
                        })
                    }
                }
            }
            if (settings.endMode == TimerEndMode.BREAK) {
                OutlinedTextField(
                    value = settings.breakMinutes.toString(),
                    onValueChange = { value ->
                        settings = settings.copy(breakMinutes = value.filter(Char::isDigit).take(2).toIntOrNull() ?: 1)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Перерыв, мин") },
                    singleLine = true
                )
                TextButton(onClick = { settings = settings.normalized(); store.savePreferences(settings) }) {
                    Text("Сохранить настройки")
                }
            } else {
                TextButton(onClick = { settings = settings.copy(defaultLimitMinutes = limitText.toIntOrNull()?.coerceIn(1, 240) ?: 25); store.savePreferences(settings) }) {
                    Text("Сделать лимит основным")
                }
            }
        }
    }
}

private fun timerModeLabel(mode: TimerEndMode): String = when (mode) {
    TimerEndMode.SIGNAL_ONLY -> "Только сигнал"
    TimerEndMode.STOP -> "Остановить"
    TimerEndMode.BREAK -> "Перерыв"
}

private fun formatTrackedTime(millis: Long): String {
    val totalMinutes = millis.coerceAtLeast(0L) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours} ч ${minutes} мин" else "${minutes} мин"
}

private fun startTimer(context: Context, taskId: Long, limitText: String, defaultLimit: Int) {
    val minutes = limitText.toIntOrNull()?.coerceIn(1, 240) ?: defaultLimit
    val intent = Intent(context, TaskTimerService::class.java)
        .setAction(ACTION_TASK_TIMER_START)
        .putExtra("task_id", taskId)
        .putExtra("limit_minutes", minutes)
    if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, intent)
    else context.startService(intent)
}
