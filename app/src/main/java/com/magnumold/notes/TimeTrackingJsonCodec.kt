package com.magnumold.notes

import org.json.JSONArray
import org.json.JSONObject

internal fun timeTrackingToJson(state: TimeTrackingState): JSONObject {
    val sessions = JSONArray()
    state.sessions.forEach { s ->
        sessions.put(JSONObject()
            .put("id", s.id).put("taskId", s.taskId)
            .put("parentTaskId", s.parentTaskId ?: JSONObject.NULL)
            .put("taskTitle", s.taskTitle).put("subtaskTitle", s.subtaskTitle ?: JSONObject.NULL)
            .put("startedAt", s.startedAtMillis).put("endedAt", s.endedAtMillis)
            .put("duration", s.durationMillis).put("endReason", s.endReason.name))
    }
    val active = JSONArray()
    state.activeSessions.values.forEach { s ->
        active.put(JSONObject()
            .put("id", s.id).put("taskId", s.taskId)
            .put("parentTaskId", s.parentTaskId ?: JSONObject.NULL)
            .put("taskTitle", s.taskTitle).put("subtaskTitle", s.subtaskTitle ?: JSONObject.NULL)
            .put("startedAt", s.startedAtMillis))
    }
    val timer = state.protectionTimer?.let { t ->
        JSONObject()
            .put("taskId", t.taskId).put("parentTaskId", t.parentTaskId ?: JSONObject.NULL).put("taskTitle", t.taskTitle)
            .put("subtaskTitle", t.subtaskTitle ?: JSONObject.NULL)
            .put("startedAt", t.startedAtMillis).put("endsAt", t.endsAtMillis)
            .put("endMode", t.endMode.name).put("soundUri", t.soundUri ?: JSONObject.NULL)
            .put("soundEnabled", t.soundEnabled)
    } ?: JSONObject.NULL
    return JSONObject().put("sessions", sessions).put("activeSessions", active).put("protectionTimer", timer)
        .put("soundUri", state.soundUri ?: JSONObject.NULL).put("soundEnabled", state.soundEnabled)
}

internal fun parseTimeTrackingJson(json: JSONObject?): TimeTrackingState {
    if (json == null) return TimeTrackingState()
    val sessionsArray = json.optJSONArray("sessions") ?: JSONArray()
    val sessions = (0 until sessionsArray.length()).mapNotNull { index ->
        val o = sessionsArray.optJSONObject(index) ?: return@mapNotNull null
        runCatching {
            WorkSession(
                id = o.getString("id"),
                taskId = o.getLong("taskId"),
                parentTaskId = if (o.isNull("parentTaskId")) null else o.getLong("parentTaskId"),
                taskTitle = o.getString("taskTitle"),
                subtaskTitle = if (o.isNull("subtaskTitle")) null else o.getString("subtaskTitle"),
                startedAtMillis = o.getLong("startedAt"),
                endedAtMillis = o.getLong("endedAt"),
                durationMillis = o.getLong("duration"),
                endReason = WorkSessionEndReason.valueOf(o.getString("endReason"))
            )
        }.getOrNull()
    }
    val activeArray = json.optJSONArray("activeSessions") ?: JSONArray()
    val active = linkedMapOf<Long, ActiveWorkSession>()
    (0 until activeArray.length()).forEach { index ->
        val o = activeArray.optJSONObject(index) ?: return@forEach
        runCatching {
            val item = ActiveWorkSession(
                id = o.getString("id"),
                taskId = o.getLong("taskId"),
                parentTaskId = if (o.isNull("parentTaskId")) null else o.getLong("parentTaskId"),
                taskTitle = o.getString("taskTitle"),
                subtaskTitle = if (o.isNull("subtaskTitle")) null else o.getString("subtaskTitle"),
                startedAtMillis = o.getLong("startedAt")
            )
            active[item.taskId] = item
        }
    }
    val timerObject = json.optJSONObject("protectionTimer")
    val timer = timerObject?.let { o ->
        runCatching {
            ProtectionTimer(
                taskId = o.getLong("taskId"),
                taskTitle = o.getString("taskTitle"),
                subtaskTitle = if (o.isNull("subtaskTitle")) null else o.getString("subtaskTitle"),
                startedAtMillis = o.getLong("startedAt"),
                endsAtMillis = o.getLong("endsAt"),
                endMode = ProtectionEndMode.valueOf(o.getString("endMode")),
                soundUri = if (o.isNull("soundUri")) null else o.getString("soundUri"),
                soundEnabled = o.optBoolean("soundEnabled", true),
                parentTaskId = if (o.isNull("parentTaskId")) null else o.getLong("parentTaskId")
            )
        }.getOrNull()
    }
    val soundUri = if (json.isNull("soundUri")) null else json.optString("soundUri").takeIf { it.isNotBlank() }
    return TimeTrackingState(sessions, active, timer, soundUri, json.optBoolean("soundEnabled", true))
}
