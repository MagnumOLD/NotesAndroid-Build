package com.magnumold.notes

import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Durable store for time tracking. Unlike debounced note autosave, timer/session
 * transitions are committed synchronously so a process kill cannot discard them.
 */
internal class TimeTrackingRepository(
    private val preferences: SharedPreferences
) {
    @Synchronized
    fun load(): TimeTrackingState = runCatching {
        val raw = preferences.getString(KEY_STATE, null) ?: return TimeTrackingState()
        parseTimeTrackingJson(JSONObject(raw))
    }.getOrDefault(TimeTrackingState())

    @Synchronized
    fun save(state: TimeTrackingState) {
        check(preferences.edit().putString(KEY_STATE, timeTrackingToJson(state).toString()).commit()) {
            "Не удалось сохранить учёт времени"
        }
    }

    @Synchronized
    fun restoreBackup(backupState: TimeTrackingState) {
        save(backupState)
    }

    @Synchronized
    fun update(transform: (TimeTrackingState) -> TimeTrackingState): TimeTrackingState {
        val updated = transform(load())
        save(updated)
        return updated
    }

    private companion object {
        const val KEY_STATE = "time_tracking_state_v1"
    }
}
