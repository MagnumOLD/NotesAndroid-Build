package com.magnumold.notes

/**
 * Immutable records for actual work tracking. Titles are snapshots so history survives
 * renaming, archiving, and deletion of the source task.
 */
internal enum class WorkSessionEndReason {
    PAUSE,
    STOP,
    TASK_COMPLETED,
    AUTOMATIC_PAUSE
}

internal data class WorkSession(
    val id: String,
    val taskId: Long,
    val parentTaskId: Long?,
    val taskTitle: String,
    val subtaskTitle: String?,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val durationMillis: Long,
    val endReason: WorkSessionEndReason
) {
    init {
        require(id.isNotBlank())
        require(taskTitle.isNotBlank())
        require(endedAtMillis >= startedAtMillis)
        require(durationMillis >= 0L)
        require(durationMillis == endedAtMillis - startedAtMillis)
    }

    fun withDuration(newDurationMillis: Long): WorkSession {
        require(newDurationMillis >= 0L)
        return copy(
            endedAtMillis = startedAtMillis + newDurationMillis,
            durationMillis = newDurationMillis
        )
    }

    fun withInterval(newStartMillis: Long, newEndMillis: Long): WorkSession {
        require(newEndMillis >= newStartMillis)
        return copy(
            startedAtMillis = newStartMillis,
            endedAtMillis = newEndMillis,
            durationMillis = newEndMillis - newStartMillis
        )
    }
}

internal data class ActiveWorkSession(
    val id: String,
    val taskId: Long,
    val parentTaskId: Long?,
    val taskTitle: String,
    val subtaskTitle: String?,
    val startedAtMillis: Long
)

internal data class TimeTrackingState(
    val sessions: List<WorkSession> = emptyList(),
    val activeSessions: Map<Long, ActiveWorkSession> = emptyMap(),
    val protectionTimer: ProtectionTimer? = null,
    val soundUri: String? = null,
    val soundEnabled: Boolean = true
)

internal enum class ProtectionEndMode {
    SIGNAL_ONLY,
    STOP_AND_PAUSE
}

/** One app-wide protection timer; independent from all actual-work sessions. */
internal data class ProtectionTimer(
    val taskId: Long,
    val taskTitle: String,
    val subtaskTitle: String?,
    val startedAtMillis: Long,
    val endsAtMillis: Long,
    val endMode: ProtectionEndMode,
    val soundUri: String? = null,
    val soundEnabled: Boolean = true
) {
    init {
        require(taskTitle.isNotBlank())
        require(endsAtMillis > startedAtMillis)
    }
}

internal data class TimeTrackingPeriodStats(
    val totalMillis: Long,
    val taskTotalsMillis: Map<Long, Long>,
    val sessionCount: Int
)
