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
    val soundEnabled: Boolean = true,
    val parentTaskId: Long? = null
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

internal fun TimeTrackingState.remapTaskIds(mapping: Map<Long, Long>): TimeTrackingState {
    if (mapping.isEmpty()) return this
    fun remap(id: Long?): Long? = id?.let { mapping[it] ?: it }
    val remappedActive = activeSessions.values.associate { active ->
        val taskId = mapping[active.taskId] ?: active.taskId
        taskId to active.copy(
            taskId = taskId,
            parentTaskId = remap(active.parentTaskId)
        )
    }
    return copy(
        sessions = sessions.map { session ->
            session.copy(
                taskId = mapping[session.taskId] ?: session.taskId,
                parentTaskId = remap(session.parentTaskId)
            )
        },
        activeSessions = remappedActive,
        protectionTimer = protectionTimer?.let { timer ->
            timer.copy(
                taskId = mapping[timer.taskId] ?: timer.taskId,
                parentTaskId = remap(timer.parentTaskId)
            )
        }
    )
}
