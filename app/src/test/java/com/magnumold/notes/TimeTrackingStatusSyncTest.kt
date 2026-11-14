package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeTrackingStatusSyncTest {
    private fun task(id: Long, text: String, status: RecordStatus, children: List<Note> = emptyList()) =
        Note(id, "", listOf(NoteBlock(text)), 1L, type = RecordType.TASK, status = status, children = children)

    @Test fun startingParentDoesNotStartItsOwnStopwatch() {
        val parent = task(10, "Parent", RecordStatus.IN_PROGRESS)
        val state = syncWorkSessionsForTaskChanges(emptyList(), listOf(parent), TimeTrackingState(), 100L)
        assertTrue(state.activeSessions.isEmpty())
    }

    @Test fun startingSubtaskStartsOnlyItsOwnSessionAndSnapshotsBothNames() {
        val before = task(10, "Parent", RecordStatus.IN_PROGRESS, listOf(task(11, "Child", RecordStatus.WAITING)))
        val after = before.copy(children = listOf(task(11, "Child", RecordStatus.IN_PROGRESS)))
        val state = syncWorkSessionsForTaskChanges(listOf(before), listOf(after), TimeTrackingState(), 100L)
        assertEquals(setOf(11L), state.activeSessions.keys)
        assertEquals(10L, state.activeSessions[11L]?.parentTaskId)
        assertEquals("Parent", state.activeSessions[11L]?.taskTitle)
        assertEquals("Child", state.activeSessions[11L]?.subtaskTitle)
    }

    @Test fun pausingSubtaskSavesSessionWithPauseReason() {
        val before = task(10, "Parent", RecordStatus.WAITING, listOf(task(11, "Child", RecordStatus.IN_PROGRESS)))
        val active = syncWorkSessionsForTaskChanges(emptyList(), listOf(before), TimeTrackingState(), 100L)
        val after = before.copy(children = listOf(task(11, "Child", RecordStatus.PAUSED)))
        val state = syncWorkSessionsForTaskChanges(listOf(before), listOf(after), active, 600L)
        assertFalse(state.activeSessions.containsKey(11L))
        assertEquals(WorkSessionEndReason.PAUSE, state.sessions.single().endReason)
        assertEquals(500L, state.sessions.single().durationMillis)
    }
}
