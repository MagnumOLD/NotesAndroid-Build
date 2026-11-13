package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionOperationsTest {
    private fun note(id: Long, pinned: Boolean = false, color: Int = 0) = Note(
        id = id,
        title = "",
        blocks = listOf(NoteBlock("record $id")),
        updated = id,
        pinned = pinned,
        colorIndex = color
    )

    private fun snapshot(
        notes: List<Note> = emptyList(),
        collections: List<NoteCollection> = emptyList()
    ) = PersistenceSnapshot(notes, collections, emptyList(), emptyList())

    @Test
    fun collectionCreateRenameDeleteAndMovePreserveRecordOrder() {
        val first = NoteCollection(10, "First", listOf(note(1)))
        val second = NoteCollection(20, "Second", listOf(note(2)))
        var state = snapshot(notes = listOf(note(3)), collections = listOf(first, second))

        state = CollectionOperations.create(state, 30, "Third")
        state = CollectionOperations.rename(state, 30, "Renamed")
        state = CollectionOperations.move(state, 30, -2)

        assertEquals(listOf("Renamed", "First", "Second"), state.collections.map { it.name })

        state = CollectionOperations.delete(state, 10)
        assertEquals(listOf(1L, 3L), state.notes.map { it.id })
        assertEquals(listOf(30L, 20L), state.collections.map { it.id })
    }

    @Test
    fun transferMovesSelectedRecordsInTheirExistingOrder() {
        val source = NoteCollection(10, "Source", listOf(note(1), note(2), note(3)))
        val target = NoteCollection(20, "Target", listOf(note(4)))
        val initial = snapshot(collections = listOf(source, target))

        val moved = CollectionOperations.transfer(initial, 10, 20, setOf(3, 1))

        assertEquals(listOf(2L), moved.collections[0].notes.map { it.id })
        assertEquals(listOf(1L, 3L, 4L), moved.collections[1].notes.map { it.id })
    }

    @Test
    fun transferFromCollectionToMainListPrependsRecords() {
        val initial = snapshot(
            notes = listOf(note(3)),
            collections = listOf(NoteCollection(10, "Source", listOf(note(1), note(2))))
        )

        val moved = CollectionOperations.transfer(initial, 10, null, setOf(2, 1))

        assertEquals(listOf(2L, 1L, 3L), moved.notes.map { it.id })
        assertTrue(moved.collections.single().notes.isEmpty())
    }

    @Test
    fun recordListCreateUpdateRemoveAndColorChangeAreScopedToSelectedCollection() {
        val initial = snapshot(
            notes = listOf(note(1)),
            collections = listOf(NoteCollection(10, "Work", listOf(note(2))))
        )

        val created = RecordListOperations.create(initial, 10, note(3))
        assertEquals(listOf(3L, 2L), created.collections.single().notes.map { it.id })
        assertEquals(listOf(1L), created.notes.map { it.id })

        val changed = RecordListOperations.changeColor(created, 10, 2, 5)
        assertEquals(5, changed.collections.single().notes.last().colorIndex)

        val updated = RecordListOperations.update(changed, 10, note(2).copy(titleText = "updated"))
        assertEquals("updated", updated.collections.single().notes.last().titleText)

        val removed = RecordListOperations.remove(updated, 10, 3)
        assertEquals(listOf(2L), removed.collections.single().notes.map { it.id })
        assertEquals(listOf(1L), removed.notes.map { it.id })
        assertSame(removed, RecordListOperations.remove(removed, 10, 999))
    }

    @Test
    fun pinToggleChangesOnlyRequestedRecordsAndKeepsPinnedRecordsFirst() {
        val initial = snapshot(notes = listOf(note(1), note(2, pinned = true), note(3)))

        val pinned = RecordListOperations.togglePinned(initial, null, setOf(3))

        assertEquals(listOf(2L, 3L, 1L), pinned.notes.map { it.id })
        assertEquals(listOf(true, true, false), pinned.notes.map { it.pinned })

        val unpinned = RecordListOperations.togglePinned(pinned, null, setOf(2, 3))
        assertEquals(listOf(2L, 3L, 1L), unpinned.notes.map { it.id })
        assertTrue(unpinned.notes.none { it.pinned })
    }
}
