package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordChildrenUpdateTest {

    private fun child(id: Long, status: RecordStatus) = Note(
        id = id,
        title = "",
        blocks = listOf(NoteBlock("item $id")),
        updated = id,
        status = status
    )

    private fun parent(
        type: RecordType = RecordType.TASK,
        status: RecordStatus = RecordStatus.IN_PROGRESS,
        children: List<Note> = emptyList(),
        previousStatus: RecordStatus? = null
    ) = Note(
        id = 100,
        title = "parent",
        blocks = listOf(NoteBlock("parent")),
        updated = 100,
        type = type,
        status = status,
        children = children,
        statusBeforeAutoCompletion = previousStatus
    )

    @Test
    fun completesParentWhenEveryChildIsCompleted() {
        val result = withUpdatedRecordChildren(
            parent(children = listOf(child(1, RecordStatus.WAITING), child(2, RecordStatus.COMPLETED))),
            listOf(child(1, RecordStatus.COMPLETED), child(2, RecordStatus.COMPLETED))
        )

        assertEquals(RecordStatus.COMPLETED, result.status)
        assertEquals(RecordStatus.IN_PROGRESS, result.statusBeforeAutoCompletion)
    }

    @Test
    fun restoresPreviousParentStatusWhenAChildIsReopened() {
        val result = withUpdatedRecordChildren(
            parent(
                status = RecordStatus.COMPLETED,
                children = listOf(child(1, RecordStatus.COMPLETED), child(2, RecordStatus.COMPLETED)),
                previousStatus = RecordStatus.IN_PROGRESS
            ),
            listOf(child(1, RecordStatus.WAITING), child(2, RecordStatus.COMPLETED))
        )

        assertEquals(RecordStatus.IN_PROGRESS, result.status)
        assertNull(result.statusBeforeAutoCompletion)
    }

    @Test
    fun doesNotCompleteParentWhileAnyChildIsIncomplete() {
        val result = withUpdatedRecordChildren(
            parent(children = listOf(child(1, RecordStatus.WAITING))),
            listOf(child(1, RecordStatus.IN_PROGRESS))
        )

        assertEquals(RecordStatus.IN_PROGRESS, result.status)
        assertNull(result.statusBeforeAutoCompletion)
    }

    @Test
    fun removingAllCompletedChildrenRestoresPreviousParentStatus() {
        val result = withUpdatedRecordChildren(
            parent(
                status = RecordStatus.COMPLETED,
                children = listOf(child(1, RecordStatus.COMPLETED)),
                previousStatus = RecordStatus.IN_PROGRESS
            ),
            emptyList()
        )

        assertEquals(RecordStatus.IN_PROGRESS, result.status)
        assertNull(result.statusBeforeAutoCompletion)
        assertEquals(emptyList<Note>(), result.children)
    }

    @Test
    fun purchaseParentCompletesWhenEveryGoodIsChecked() {
        val result = withUpdatedRecordChildren(
            parent(
                type = RecordType.PURCHASE,
                children = listOf(child(1, RecordStatus.WAITING), child(2, RecordStatus.WAITING))
            ),
            listOf(child(1, RecordStatus.COMPLETED), child(2, RecordStatus.COMPLETED))
        )

        assertEquals(RecordStatus.COMPLETED, result.status)
        assertEquals(RecordStatus.IN_PROGRESS, result.statusBeforeAutoCompletion)
    }
}
