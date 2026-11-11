package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test

class NoteReorderModelTest {

    private fun note(id: Long, pinned: Boolean = false) =
        Note(id, "", listOf(NoteBlock("note $id")), id, pinned = pinned)

    @Test
    fun createsOneGroupPlaceholderAtTheGrabbedRowsOriginalPosition() {
        val notes = listOf(note(1), note(2), note(3), note(4))

        val rows = NoteReorderModel.createDragRows(
            orderedNotes = notes,
            movingIds = setOf(2, 4),
            grabbedNoteId = 4
        )

        assertEquals(listOf("1", "3", "4"), rows?.map { it.key })
        assertEquals(listOf(2L, 4L), rows?.last()?.notes?.map { it.id })
    }

    @Test
    fun returnsTheRowImmediatelyBeforeTheMovingGroupAsInsertionAnchor() {
        val notes = listOf(note(1), note(2), note(3), note(4))
        val rows = NoteReorderModel.createDragRows(notes, setOf(2, 4), 4)!!

        assertEquals(3L, NoteReorderModel.afterId(rows, setOf(2, 4)))
    }

    @Test
    fun returnsNullWhenTheMovingGroupIsAtTheBeginning() {
        val rows = NoteReorderModel.createDragRows(
            listOf(note(1), note(2), note(3)),
            setOf(1, 3),
            1
        )!!

        assertNull(NoteReorderModel.afterId(rows, setOf(1, 3)))
    }

    @Test
    fun movesAnUnpinnedNoteAfterTargetWithoutCrossingPinnedPartition() {
        val notes = listOf(note(1, true), note(2, true), note(3), note(4))

        val moved = NoteReorderModel.moveSelected(notes, setOf(4), afterId = 1)

        assertEquals(listOf(1L, 2L, 4L, 3L), moved.map { it.id })
        assertEquals(listOf(true, true, false, false), moved.map { it.pinned })
    }

    @Test
    fun movesPinnedGroupAsOneStableOrderedBlockInsidePinnedPartition() {
        val notes = listOf(note(1, true), note(2, true), note(3, true), note(4, true), note(5))

        val moved = NoteReorderModel.moveSelected(notes, setOf(2, 3), afterId = null)

        assertEquals(listOf(2L, 3L, 1L, 4L, 5L), moved.map { it.id })
        assertEquals(listOf(true, true, true, true, false), moved.map { it.pinned })
    }

    @Test
    fun rejectsMixedPinnedGroupWithoutChangingOriginalList() {
        val notes = listOf(note(1, true), note(2), note(3))

        val moved = NoteReorderModel.moveSelected(notes, setOf(1, 2), afterId = 3)

        assertEquals(notes, moved)
        assertSame(notes, moved)
    }

    @Test
    fun missingInsertionAnchorPlacesNoteAtEndOfItsOwnPartition() {
        val notes = listOf(note(1, true), note(2), note(3))

        val moved = NoteReorderModel.moveSelected(notes, setOf(2), afterId = 999)

        assertEquals(listOf(1L, 3L, 2L), moved.map { it.id })
    }

    @Test
    fun ignoresUnknownIdsAndInvalidGrabTargets() {
        val notes = listOf(note(1), note(2))

        assertNull(NoteReorderModel.createDragRows(notes, setOf(2), 999))
        assertNull(NoteReorderModel.createDragRows(notes, setOf(999), 2))
        assertEquals(notes, NoteReorderModel.moveSelected(notes, setOf(999), null))
    }
}
