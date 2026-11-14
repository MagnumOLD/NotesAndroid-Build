package com.magnumold.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
internal fun TimeTrackingAnalyticsScreen(
    state: TimeTrackingState,
    onStateChange: (TimeTrackingState) -> Unit,
    onBack: () -> Unit,
    manualTaskId: Long?,
    manualParentTaskId: Long?,
    manualTaskTitle: String,
    manualSubtaskTitle: String?
) {
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<WorkSession?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Учёт времени", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("Назад") }
        }
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("История") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Статистика") })
        }
        if (tab == 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Рабочие сессии", style = MaterialTheme.typography.titleMedium)
                if (manualTaskId != null) {
                    TextButton(onClick = { adding = true }) { Text("Добавить") }
                }
            }
            if (state.sessions.isEmpty()) {
                Text("История пока пуста", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.sessions.sortedByDescending { it.startedAtMillis }, key = { it.id }) { session ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(session.subtaskTitle ?: session.taskTitle, style = MaterialTheme.typography.titleSmall)
                                if (session.subtaskTitle != null) {
                                    Text(session.taskTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    "${formatSessionDate(session.startedAtMillis)} — ${formatSessionDate(session.endedAtMillis)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text("Длительность: ${formatSessionDuration(session.durationMillis)}")
                                Text("Завершение: ${session.endReason.toRussianLabel()}", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { editing = session }) { Text("Изменить") }
                                    TextButton(onClick = {
                                        onStateChange(TimeTrackingEngine.deleteSession(state, session.id))
                                    }) { Text("Удалить") }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            TimeTrackingStatisticsContent(state = state)
        }
    }

    editing?.let { session ->
        EditWorkSessionDialog(
            session = session,
            onDismiss = { editing = null },
            onSave = { edited ->
                onStateChange(TimeTrackingEngine.replaceSession(state, edited))
                editing = null
            }
        )
    }
    if (adding && manualTaskId != null) {
        AddWorkSessionDialog(
            taskId = manualTaskId,
            parentTaskId = manualParentTaskId,
            taskTitle = manualTaskTitle,
            subtaskTitle = manualSubtaskTitle,
            onDismiss = { adding = false },
            onSave = { session ->
                onStateChange(TimeTrackingEngine.addManualSession(state, session))
                adding = false
            }
        )
    }
}

@Composable
private fun EditWorkSessionDialog(
    session: WorkSession,
    onDismiss: () -> Unit,
    onSave: (WorkSession) -> Unit
) {
    var startText by remember(session.id) { mutableStateOf(formatSessionDate(session.startedAtMillis)) }
    var endText by remember(session.id) { mutableStateOf(formatSessionDate(session.endedAtMillis)) }
    var durationText by remember(session.id) { mutableStateOf("%.1f".format(Locale.US, session.durationMillis / 60_000.0)) }
    var error by remember(session.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить сессию") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(session.subtaskTitle ?: session.taskTitle)
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Начало (гггг-мм-дд чч:мм)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Окончание (гггг-мм-дд чч:мм)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Длительность, минут") },
                    singleLine = true
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = parseSessionDate(startText)
                val typedDuration = durationText.replace(',', '.').toDoubleOrNull()
                val typedEnd = parseSessionDate(endText)
                if (start == null || typedDuration == null || typedDuration < 0.0) {
                    error = "Проверь дату начала и длительность"
                } else {
                    val duration = (typedDuration * 60_000.0).toLong()
                    val end = if (durationText != "%.1f".format(Locale.US, session.durationMillis / 60_000.0)) {
                        start + duration
                    } else typedEnd
                    if (end == null || end < start) {
                        error = "Время окончания должно быть позже начала"
                    } else onSave(session.withInterval(start, end))
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun AddWorkSessionDialog(
    taskId: Long,
    parentTaskId: Long?,
    taskTitle: String,
    subtaskTitle: String?,
    onDismiss: () -> Unit,
    onSave: (WorkSession) -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    var startText by remember { mutableStateOf(formatSessionDate(now - 60L * 60L * 1_000L)) }
    var durationText by remember { mutableStateOf("60") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить сессию") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(subtaskTitle ?: taskTitle)
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Начало (гггг-мм-дд чч:мм)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Длительность, минут") },
                    singleLine = true
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = parseSessionDate(startText)
                val duration = durationText.toLongOrNull()
                if (start == null || duration == null || duration <= 0L) {
                    error = "Проверь дату начала и длительность"
                } else {
                    val millis = duration * 60_000L
                    onSave(
                        WorkSession(
                            id = UUID.randomUUID().toString(),
                            taskId = taskId,
                            parentTaskId = parentTaskId,
                            taskTitle = taskTitle,
                            subtaskTitle = subtaskTitle,
                            startedAtMillis = start,
                            endedAtMillis = start + millis,
                            durationMillis = millis,
                            endReason = WorkSessionEndReason.STOP
                        )
                    )
                }
            }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun ColumnScope.TimeTrackingStatisticsContent(state: TimeTrackingState) {
    var period by remember { mutableStateOf("День") }
    var customStart by remember { mutableStateOf(formatSessionDay(System.currentTimeMillis())) }
    var customEnd by remember { mutableStateOf(formatSessionDay(System.currentTimeMillis())) }
    var compareStart by remember { mutableStateOf("") }
    var compareEnd by remember { mutableStateOf("") }
    val now = System.currentTimeMillis()
    val range = remember(period, customStart, customEnd, now / 60_000L) {
        periodBounds(period, customStart, customEnd, now)
    }
    val stats = TimeTrackingEngine.statisticsBetween(state, range.first, range.second, now)
    val duration = range.second - range.first
    val previousRange = remember(period, range.first, range.second, compareStart, compareEnd) {
        if (period == "Диапазон" && compareStart.isNotBlank() && compareEnd.isNotBlank()) {
            periodBounds("Диапазон", compareStart, compareEnd, now)
        } else previousPeriodBounds(period, range.first, range.second)
    }
    val previous = TimeTrackingEngine.statisticsBetween(
        state, previousRange.first, previousRange.second, now
    )
    val delta = stats.totalMillis - previous.totalMillis
    val dailyTotals = TimeTrackingEngine.dailyTotals(state, now)
    val barColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("День", "Неделя", "Месяц", "Диапазон").forEach { value ->
                OutlinedButton(onClick = { period = value }) { Text(value) }
            }
        }
        if (period == "Диапазон") {
            OutlinedTextField(
                value = customStart,
                onValueChange = { customStart = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("С даты (гггг-мм-дд)") },
                singleLine = true
            )
            OutlinedTextField(
                value = customEnd,
                onValueChange = { customEnd = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("По дату включительно") },
                singleLine = true
            )
            Text("Сравнить с другим периодом (необязательно)", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = compareStart,
                onValueChange = { compareStart = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Начало периода сравнения") },
                singleLine = true
            )
            OutlinedTextField(
                value = compareEnd,
                onValueChange = { compareEnd = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Окончание периода сравнения") },
                singleLine = true
            )
        }
        Text("Фактическое время", style = MaterialTheme.typography.titleMedium)
        Text(formatSessionDuration(stats.totalMillis), style = MaterialTheme.typography.headlineMedium)
        Text("Рабочих сессий: ${stats.sessionCount}")
        Text(
            "По сравнению с предыдущим периодом: ${if (delta >= 0) "+" else "−"}${formatSessionDuration(kotlin.math.abs(delta))}",
            color = if (delta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Text("Изменение по задачам", style = MaterialTheme.typography.titleSmall)
        val taskIds = (stats.taskTotalsMillis.keys + previous.taskTotalsMillis.keys).toSet()
        data class BreakdownRow(
            val id: Long,
            val parentId: Long?,
            val parentTitle: String,
            val title: String
        )
        val rows = taskIds.map { id ->
            val saved = state.sessions.firstOrNull { it.taskId == id }
            val active = state.activeSessions[id]
            val parentId = saved?.parentTaskId ?: active?.parentTaskId
            val parentTitle = saved?.taskTitle ?: active?.taskTitle ?: "Задача"
            val subtaskTitle = saved?.subtaskTitle ?: active?.subtaskTitle
            BreakdownRow(id, parentId, parentTitle, subtaskTitle ?: parentTitle)
        }
        rows.groupBy { it.parentId ?: it.id }.values.forEach { group ->
            val parentTitle = group.first().parentTitle
            val currentGroup = group.sumOf { stats.taskTotalsMillis[it.id] ?: 0L }
            val previousGroup = group.sumOf { previous.taskTotalsMillis[it.id] ?: 0L }
            val groupDelta = currentGroup - previousGroup
            Text(
                "$parentTitle — ${formatSessionDuration(currentGroup)} ( ${if (groupDelta >= 0) "+" else "−"}${formatSessionDuration(kotlin.math.abs(groupDelta))} )",
                style = MaterialTheme.typography.titleSmall
            )
            group.forEach { row ->
                val current = stats.taskTotalsMillis[row.id] ?: 0L
                val old = previous.taskTotalsMillis[row.id] ?: 0L
                val difference = current - old
                Text(
                    "  ${row.title} — ${formatSessionDuration(current)} ( ${if (difference >= 0) "+" else "−"}${formatSessionDuration(kotlin.math.abs(difference))} )",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text("Изменение по дням", style = MaterialTheme.typography.titleSmall)
        val chartData = dailyTotals.filter { (day, _) ->
            val dayStart = parseSessionDay(day) ?: 0L
            dayStart < range.second && dayStart + 86_400_000L > range.first
        }.toList().sortedBy { it.first }
        if (chartData.isEmpty()) {
            Text("За этот период записей нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                val maxValue = (chartData.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L).toFloat()
                val gap = 6.dp.toPx()
                val barWidth = ((size.width - gap * (chartData.size - 1)) / chartData.size).coerceAtLeast(2f)
                chartData.forEachIndexed { index, entry ->
                    val height = (entry.second / maxValue * size.height).coerceAtLeast(2f)
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(index * (barWidth + gap), size.height - height),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
        }
    }
}

private fun periodBounds(period: String, customStart: String, customEnd: String, now: Long): Pair<Long, Long> {
    val calendar = Calendar.getInstance().apply { timeInMillis = now }
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return when (period) {
        "Неделя" -> {
            val day = calendar.get(Calendar.DAY_OF_WEEK)
            val offset = if (day == Calendar.SUNDAY) -6 else Calendar.MONDAY - day
            calendar.add(Calendar.DAY_OF_MONTH, offset)
            val start = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH, 7)
            start to calendar.timeInMillis
        }
        "Месяц" -> {
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            val start = calendar.timeInMillis
            calendar.add(Calendar.MONTH, 1)
            start to calendar.timeInMillis
        }
        "Диапазон" -> {
            val start = parseSessionDay(customStart) ?: calendar.timeInMillis
            val endDay = parseSessionDay(customEnd) ?: start
            val endCalendar = Calendar.getInstance().apply { timeInMillis = endDay }
            endCalendar.add(Calendar.DAY_OF_MONTH, 1)
            start to maxOf(start, endCalendar.timeInMillis)
        }
        else -> {
            val start = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            start to calendar.timeInMillis
        }
    }
}

private fun previousPeriodBounds(period: String, start: Long, end: Long): Pair<Long, Long> {
    return when (period) {
        "День" -> {
            val calendar = Calendar.getInstance().apply { timeInMillis = start }
            calendar.add(Calendar.DAY_OF_MONTH, -1)
            calendar.timeInMillis to start
        }
        "Неделя" -> {
            val previousStart = Calendar.getInstance().apply { timeInMillis = start }
                .apply { add(Calendar.DAY_OF_MONTH, -7) }.timeInMillis
            previousStart to start
        }
        "Месяц" -> {
            val previousStart = Calendar.getInstance().apply { timeInMillis = start }
                .apply { add(Calendar.MONTH, -1) }.timeInMillis
            previousStart to start
        }
        else -> {
            val duration = end - start
            (start - duration) to start
        }
    }
}

private fun formatSessionDate(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatSessionDay(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))

private fun parseSessionDate(value: String): Long? =
    runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply { isLenient = false }.parse(value)?.time
    }.getOrNull()

private fun parseSessionDay(value: String): Long? =
    runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { isLenient = false }.parse(value)?.time
    }.getOrNull()

private fun formatSessionDuration(durationMillis: Long): String {
    val seconds = durationMillis.coerceAtLeast(0L) / 1_000L
    val hours = seconds / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    return if (hours > 0L) "${hours} ч ${minutes} мин" else "${minutes} мин"
}

private fun WorkSessionEndReason.toRussianLabel(): String = when (this) {
    WorkSessionEndReason.PAUSE -> "Пауза"
    WorkSessionEndReason.STOP -> "Остановка"
    WorkSessionEndReason.TASK_COMPLETED -> "Завершение задачи"
    WorkSessionEndReason.AUTOMATIC_PAUSE -> "Автоматическая пауза"
}
