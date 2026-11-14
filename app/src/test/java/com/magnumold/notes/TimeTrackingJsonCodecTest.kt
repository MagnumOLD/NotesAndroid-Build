package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.json.JSONObject

class TimeTrackingJsonCodecTest {
    @Test fun trackingStateRoundTripsSessionsActiveStopwatchesAndTimer() {
        val session = WorkSession(
            id = "session-1",
            taskId = 2L,
            parentTaskId = 1L,
            taskTitle = "Parent",
            subtaskTitle = "Subtask",
            startedAtMillis = 100L,
            endedAtMillis = 500L,
            durationMillis = 400L,
            endReason = WorkSessionEndReason.PAUSE
        )
        val active = ActiveWorkSession(
            id = "session-2",
            taskId = 3L,
            parentTaskId = 1L,
            taskTitle = "Parent",
            subtaskTitle = "Another subtask",
            startedAtMillis = 600L
        )
        val timer = ProtectionTimer(
            taskId = 2L,
            taskTitle = "Parent",
            subtaskTitle = "Subtask",
            startedAtMillis = 700L,
            endsAtMillis = 70_700L,
            endMode = ProtectionEndMode.STOP_AND_PAUSE,
            soundUri = "content://alarm/tone",
            soundEnabled = false,
            parentTaskId = 1L
        )
        val original = TimeTrackingState(
            sessions = listOf(session),
            activeSessions = mapOf(3L to active),
            protectionTimer = timer,
            soundUri = "content://alarm/tone",
            soundEnabled = false
        )
        val restored = parseTimeTrackingJson(timeTrackingToJson(original))
        assertEquals(original, restored)
    }

    @Test fun missingTrackingObjectIsNotTreatedAsAnImportedEmptyHistory() {
        val backup = parseBackupJson(
            """{"format":"notes_backup","version":6,"notes":[],"collections":[]}"""
        )
        assertNull(backup.timeTracking)
    }
}
