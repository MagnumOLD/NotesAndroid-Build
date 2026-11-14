package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeTrackingEngineTest {
    @Test fun parallelSessionsAreCountedIndependently() {
        var state = TimeTrackingState()
        state = TimeTrackingEngine.start(state, 1, 100, "Parent", "A", 0, "a")
        state = TimeTrackingEngine.start(state, 2, 100, "Parent", "B", 0, "b")
        state = TimeTrackingEngine.finish(state, 1, 3_600_000, WorkSessionEndReason.STOP)
        state = TimeTrackingEngine.finish(state, 2, 3_600_000, WorkSessionEndReason.STOP)
        assertEquals(3_600_000L, TimeTrackingEngine.taskDurationMillis(state, 1, 3_600_000))
        assertEquals(3_600_000L, TimeTrackingEngine.taskDurationMillis(state, 2, 3_600_000))
        assertEquals(7_200_000L, TimeTrackingEngine.parentDurationMillis(state, 100, 3_600_000))
    }

    @Test fun pausingClosesSessionAndNextStartCreatesAnother() {
        var state = TimeTrackingEngine.start(TimeTrackingState(), 1, null, "Task", null, 100, "first")
        state = TimeTrackingEngine.finish(state, 1, 500, WorkSessionEndReason.PAUSE)
        state = TimeTrackingEngine.start(state, 1, null, "Task", null, 900, "second")
        assertEquals(1, state.sessions.size)
        assertEquals(900L, state.activeSessions[1L]?.startedAtMillis)
    }

    @Test fun taskTitleSnapshotSurvivesIndependentOfTaskRecord() {
        var state = TimeTrackingEngine.start(TimeTrackingState(), 7, null, "Original title", "Child", 10, "s")
        state = TimeTrackingEngine.finish(state, 7, 20, WorkSessionEndReason.TASK_COMPLETED)
        assertEquals("Original title", state.sessions.single().taskTitle)
        assertEquals("Child", state.sessions.single().subtaskTitle)
    }

    @Test fun sessionCanBeEditedAddedAndDeleted() {
        val session = WorkSession("s", 1, null, "Task", null, 10, 30, 20, WorkSessionEndReason.STOP)
        var state = TimeTrackingEngine.addManualSession(TimeTrackingState(), session)
        state = TimeTrackingEngine.replaceSession(state, session.copy(endedAtMillis = 50, durationMillis = 40))
        assertEquals(40L, state.sessions.single().durationMillis)
        state = TimeTrackingEngine.deleteSession(state, "s")
        assertTrue(state.sessions.isEmpty())
    }

    @Test fun protectionTimerIsIndependentAndOnlyOneCanRun() {
        val timer = ProtectionTimerEngine.start(null, 1, "Task", "Child", 0, 60_000, ProtectionEndMode.SIGNAL_ONLY, null, false)
        assertEquals(30_000L, ProtectionTimerEngine.remainingMillis(timer, 30_000))
        assertTrue(ProtectionTimerEngine.isFinished(timer, 60_000))
        assertThrows(IllegalArgumentException::class.java) {
            ProtectionTimerEngine.start(timer, 2, "Other", null, 0, 60_000, ProtectionEndMode.STOP_AND_PAUSE, null, true)
        }
    }
}
