package com.magnumold.notes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal class TaskTimerStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("task_time_tracking", Context.MODE_PRIVATE)

    @Synchronized
    fun preferences(): TimerPreferences {
        val mode = runCatching { TimerEndMode.valueOf(prefs.getString("end_mode", "SIGNAL_ONLY")!!) }
            .getOrDefault(TimerEndMode.SIGNAL_ONLY)
        return TimerPreferences(
            defaultLimitMinutes = prefs.getInt("default_limit_minutes", 25),
            endMode = mode,
            breakMinutes = prefs.getInt("break_minutes", 5),
            soundEnabled = prefs.getBoolean("sound_enabled", true)
        ).normalized()
    }

    @Synchronized
    fun savePreferences(value: TimerPreferences) {
        val normalized = value.normalized()
        prefs.edit()
            .putInt("default_limit_minutes", normalized.defaultLimitMinutes)
            .putString("end_mode", normalized.endMode.name)
            .putInt("break_minutes", normalized.breakMinutes)
            .putBoolean("sound_enabled", normalized.soundEnabled)
            .apply()
    }

    @Synchronized
    fun activeTimer(): ActiveTaskTimer? {
        if (!prefs.contains("active_task_id")) return null
        return runCatching {
            ActiveTaskTimer(
                taskId = prefs.getLong("active_task_id", 0L),
                startedAtMillis = prefs.getLong("started_at", 0L),
                deadlineMillis = prefs.getLong("deadline_at", 0L),
                alerted = prefs.getBoolean("alerted", false),
                breakUntilMillis = prefs.getLong("break_until", 0L).takeIf { it > 0L }
            )
        }.getOrNull()
    }

    @Synchronized
    fun start(taskId: Long, now: Long, limitMinutes: Int) {
        prefs.edit()
            .putLong("active_task_id", taskId)
            .putLong("started_at", now)
            .putLong("deadline_at", now + limitMinutes.coerceIn(1, 240) * 60_000L)
            .putBoolean("alerted", false)
            .remove("break_until")
            .apply()
    }

    @Synchronized
    fun markAlerted() { prefs.edit().putBoolean("alerted", true).apply() }

    @Synchronized
    fun beginBreak(untilMillis: Long) { prefs.edit().putLong("break_until", untilMillis).apply() }

    @Synchronized
    fun clearActive() { prefs.edit().remove("active_task_id").remove("started_at").remove("deadline_at").remove("alerted").remove("break_until").apply() }

    @Synchronized
    fun finishActive(endedAtMillis: Long): TaskTimeSession? {
        val active = activeTimer() ?: return null
        if (active.breakUntilMillis != null) { clearActive(); return null }
        val session = appendSession(active, endedAtMillis)
        clearActive()
        return session
    }

    @Synchronized
    fun finishActiveForBreak(endedAtMillis: Long, breakUntilMillis: Long): TaskTimeSession? {
        val active = activeTimer() ?: return null
        if (active.breakUntilMillis != null) return null
        val session = appendSession(active, endedAtMillis)
        prefs.edit()
            .putLong("started_at", 0L)
            .putLong("deadline_at", breakUntilMillis)
            .putLong("break_until", breakUntilMillis)
            .putBoolean("alerted", false)
            .apply()
        return session
    }

    private fun appendSession(active: ActiveTaskTimer, endedAtMillis: Long): TaskTimeSession {
        val session = TaskTimeSession(
            id = UUID.randomUUID().toString(),
            taskId = active.taskId,
            startedAtMillis = active.startedAtMillis,
            endedAtMillis = endedAtMillis,
            durationMillis = TaskTimerMath.elapsed(active.startedAtMillis, endedAtMillis)
        )
        saveSessions(sessions() + session)
        return session
    }

    @Synchronized
    fun importBackup(imported: List<TaskTimeSession>, importedPreferences: TimerPreferences) {
        val merged = (sessions() + imported).distinctBy { it.id }
        saveSessions(merged)
        savePreferences(importedPreferences)
    }

    @Synchronized
    private fun saveSessions(items: List<TaskTimeSession>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("taskId", item.taskId)
                .put("startedAt", item.startedAtMillis).put("endedAt", item.endedAtMillis)
                .put("duration", item.durationMillis))
        }
        prefs.edit().putString("sessions", array.toString()).apply()
    }

    @Synchronized
    fun sessions(): List<TaskTimeSession> = runCatching {
        val array = JSONArray(prefs.getString("sessions", "[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            TaskTimeSession(
                id = item.optString("id"), taskId = item.optLong("taskId"),
                startedAtMillis = item.optLong("startedAt"), endedAtMillis = item.optLong("endedAt"),
                durationMillis = item.optLong("duration").coerceAtLeast(0L)
            )
        }
    }.getOrDefault(emptyList())

    fun totalMillis(taskId: Long): Long = TaskTimerMath.totalForTask(sessions(), taskId)
}
