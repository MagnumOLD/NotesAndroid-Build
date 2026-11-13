package com.magnumold.notes

internal enum class TimerEndMode { SIGNAL_ONLY, STOP, BREAK }

internal data class TimerPreferences(
    val defaultLimitMinutes: Int = 25,
    val endMode: TimerEndMode = TimerEndMode.SIGNAL_ONLY,
    val breakMinutes: Int = 5,
    val soundEnabled: Boolean = true
) {
    fun normalized() = copy(
        defaultLimitMinutes = defaultLimitMinutes.coerceIn(1, 240),
        breakMinutes = breakMinutes.coerceIn(1, 90)
    )
}

internal data class ActiveTaskTimer(
    val taskId: Long,
    val startedAtMillis: Long,
    val deadlineMillis: Long,
    val alerted: Boolean = false,
    val breakUntilMillis: Long? = null
) {
    fun remainingMillis(nowMillis: Long): Long =
        ((breakUntilMillis ?: deadlineMillis) - nowMillis).coerceAtLeast(0L)

    fun isOnBreak(nowMillis: Long): Boolean =
        breakUntilMillis?.let { nowMillis < it } == true

    fun isExpired(nowMillis: Long): Boolean = remainingMillis(nowMillis) == 0L
}

internal data class TaskTimeSession(
    val id: String,
    val taskId: Long,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val durationMillis: Long
)

internal object TaskTimerMath {
    fun remaining(deadlineMillis: Long, nowMillis: Long): Long =
        (deadlineMillis - nowMillis).coerceAtLeast(0L)

    fun elapsed(startedAtMillis: Long, endedAtMillis: Long): Long =
        (endedAtMillis - startedAtMillis).coerceAtLeast(0L)

    fun totalForTask(sessions: List<TaskTimeSession>, taskId: Long): Long =
        sessions.asSequence().filter { it.taskId == taskId }.sumOf { it.durationMillis }
}
