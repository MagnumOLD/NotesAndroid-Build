package com.magnumold.notes

import java.util.Calendar
import java.util.UUID

/**
 * Pure time-tracking rules. Persistence and task status changes are owned by the
 * application layer; this engine never changes task status by itself.
 */
internal object TimeTrackingEngine {
    fun start(
        state: TimeTrackingState,
        taskId: Long,
        parentTaskId: Long?,
        taskTitle: String,
        subtaskTitle: String?,
        nowMillis: Long,
        sessionId: String = UUID.randomUUID().toString()
    ): TimeTrackingState {
        require(taskId !in state.activeSessions) { "A work session is already active for this task." }
        require(taskTitle.isNotBlank())
        val active = ActiveWorkSession(
            id = sessionId,
            taskId = taskId,
            parentTaskId = parentTaskId,
            taskTitle = taskTitle,
            subtaskTitle = subtaskTitle,
            startedAtMillis = nowMillis
        )
        return state.copy(activeSessions = state.activeSessions + (taskId to active))
    }

    fun finish(
        state: TimeTrackingState,
        taskId: Long,
        nowMillis: Long,
        reason: WorkSessionEndReason
    ): TimeTrackingState {
        val active = state.activeSessions[taskId] ?: return state
        val end = maxOf(nowMillis, active.startedAtMillis)
        val completed = WorkSession(
            id = active.id,
            taskId = active.taskId,
            parentTaskId = active.parentTaskId,
            taskTitle = active.taskTitle,
            subtaskTitle = active.subtaskTitle,
            startedAtMillis = active.startedAtMillis,
            endedAtMillis = end,
            durationMillis = end - active.startedAtMillis,
            endReason = reason
        )
        return state.copy(
            sessions = state.sessions + completed,
            activeSessions = state.activeSessions - taskId
        )
    }

    fun finishAll(
        state: TimeTrackingState,
        nowMillis: Long,
        reason: WorkSessionEndReason
    ): TimeTrackingState {
        return state.activeSessions.keys.toList().fold(state) { current, id ->
            finish(current, id, nowMillis, reason)
        }
    }

    /**
     * Groups consecutive work intervals for history display only. Stored intervals,
     * elapsed-time totals, and statistics remain exact, so breaks are never counted.
     * A gap of exactly ten minutes is included in the same displayed work session.
     */
    fun groupSessionsForDisplay(
        sessions: List<WorkSession>,
        mergeGapMillis: Long = 10 * 60 * 1_000L
    ): List<List<WorkSession>> {
        require(mergeGapMillis >= 0L)
        return sessions.groupBy { it.taskId }.values.flatMap { taskSessions ->
            val sorted = taskSessions.sortedBy { it.startedAtMillis }
            val groups = mutableListOf<List<WorkSession>>()
            var current = mutableListOf(sorted.first())
            sorted.drop(1).forEach { next ->
                val previous = current.last()
                val gap = next.startedAtMillis - previous.endedAtMillis
                if (gap <= mergeGapMillis) {
                    current.add(next)
                } else {
                    groups.add(current.toList())
                    current = mutableListOf(next)
                }
            }
            groups.add(current.toList())
            groups
        }.sortedByDescending { it.first().startedAtMillis }
    }

    fun taskDurationMillis(state: TimeTrackingState, taskId: Long, nowMillis: Long): Long {
        val saved = state.sessions.asSequence()
            .filter { it.taskId == taskId }
            .sumOf { it.durationMillis }
        val active = state.activeSessions[taskId]?.let {
            (nowMillis - it.startedAtMillis).coerceAtLeast(0L)
        } ?: 0L
        return saved + active
    }

    /** Parent duration is derived only from its children; no parent stopwatch exists. */
    fun parentDurationMillis(state: TimeTrackingState, parentTaskId: Long, nowMillis: Long): Long {
        val saved = state.sessions.asSequence()
            .filter { it.parentTaskId == parentTaskId }
            .sumOf { it.durationMillis }
        val active = state.activeSessions.values.asSequence()
            .filter { it.parentTaskId == parentTaskId }
            .sumOf { (nowMillis - it.startedAtMillis).coerceAtLeast(0L) }
        return saved + active
    }

    fun replaceSession(state: TimeTrackingState, edited: WorkSession): TimeTrackingState {
        require(state.sessions.any { it.id == edited.id }) { "Session not found." }
        return state.copy(sessions = state.sessions.map { if (it.id == edited.id) edited else it })
    }

    fun addManualSession(state: TimeTrackingState, session: WorkSession): TimeTrackingState {
        require(state.sessions.none { it.id == session.id })
        require(state.activeSessions.values.none { it.id == session.id })
        return state.copy(sessions = state.sessions + session)
    }

    fun deleteSession(state: TimeTrackingState, sessionId: String): TimeTrackingState =
        state.copy(sessions = state.sessions.filterNot { it.id == sessionId })

    /** Calculates statistics for [startInclusive, endExclusive), retaining overlap per task. */
    fun statisticsBetween(
        state: TimeTrackingState,
        startInclusive: Long,
        endExclusive: Long,
        nowMillis: Long
    ): TimeTrackingPeriodStats {
        require(endExclusive >= startInclusive)
        val totals = linkedMapOf<Long, Long>()
        var count = 0
        fun include(taskId: Long, start: Long, end: Long) {
            val overlap = (minOf(end, endExclusive) - maxOf(start, startInclusive)).coerceAtLeast(0L)
            if (overlap > 0L) {
                totals[taskId] = (totals[taskId] ?: 0L) + overlap
            }
            if (start >= startInclusive && start < endExclusive) count++
        }
        state.sessions.forEach { include(it.taskId, it.startedAtMillis, it.endedAtMillis) }
        state.activeSessions.values.forEach {
            include(it.taskId, it.startedAtMillis, nowMillis.coerceAtLeast(it.startedAtMillis))
        }
        return TimeTrackingPeriodStats(totals.values.sum(), totals, count)
    }

    /**
     * Splits sessions at local midnight so daily statistics attribute overnight work
     * to the correct calendar days. Overlapping sessions remain independent.
     */
    fun dailyTotals(state: TimeTrackingState, nowMillis: Long): Map<String, Long> {
        val totals = linkedMapOf<String, Long>()
        state.sessions.forEach { addSessionByDay(totals, it.startedAtMillis, it.endedAtMillis) }
        state.activeSessions.values.forEach {
            addSessionByDay(totals, it.startedAtMillis, nowMillis.coerceAtLeast(it.startedAtMillis))
        }
        return totals
    }

    fun dailySessionCounts(state: TimeTrackingState): Map<String, Int> =
        buildMap {
            state.sessions.forEach { session ->
                val key = dayKey(session.startedAtMillis)
                put(key, (get(key) ?: 0) + 1)
            }
            state.activeSessions.values.forEach { active ->
                val key = dayKey(active.startedAtMillis)
                put(key, (get(key) ?: 0) + 1)
            }
        }

    private fun addSessionByDay(totals: MutableMap<String, Long>, start: Long, end: Long) {
        var cursor = start
        while (cursor < end) {
            val calendar = Calendar.getInstance().apply { timeInMillis = cursor }
            val dayStart = calendar.clone() as Calendar
            dayStart.set(Calendar.HOUR_OF_DAY, 0)
            dayStart.set(Calendar.MINUTE, 0)
            dayStart.set(Calendar.SECOND, 0)
            dayStart.set(Calendar.MILLISECOND, 0)
            val nextDayCalendar = dayStart.clone() as Calendar
            nextDayCalendar.add(Calendar.DAY_OF_MONTH, 1)
            val nextDay = nextDayCalendar.timeInMillis
            val segmentEnd = minOf(end, nextDay)
            val key = dayKey(cursor)
            totals[key] = (totals[key] ?: 0L) + (segmentEnd - cursor)
            cursor = segmentEnd
        }
    }

    private fun dayKey(timestamp: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = timestamp }
        return "%04d-%02d-%02d".format(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH)
        )
    }
}

internal object ProtectionTimerEngine {
    fun start(
        current: ProtectionTimer?,
        taskId: Long,
        taskTitle: String,
        subtaskTitle: String?,
        nowMillis: Long,
        durationMillis: Long,
        endMode: ProtectionEndMode,
        soundUri: String?,
        soundEnabled: Boolean,
        parentTaskId: Long? = null
    ): ProtectionTimer {
        require(current == null) { "Only one protection timer can run at a time." }
        require(durationMillis > 0L)
        return ProtectionTimer(
            taskId = taskId,
            taskTitle = taskTitle,
            subtaskTitle = subtaskTitle,
            startedAtMillis = nowMillis,
            endsAtMillis = nowMillis + durationMillis,
            endMode = endMode,
            soundUri = soundUri,
            soundEnabled = soundEnabled,
            parentTaskId = parentTaskId
        )
    }

    fun remainingMillis(timer: ProtectionTimer, nowMillis: Long): Long =
        (timer.endsAtMillis - nowMillis).coerceAtLeast(0L)

    fun isFinished(timer: ProtectionTimer, nowMillis: Long): Boolean =
        nowMillis >= timer.endsAtMillis
}
