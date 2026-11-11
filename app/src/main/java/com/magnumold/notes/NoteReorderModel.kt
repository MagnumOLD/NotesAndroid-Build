package com.magnumold.notes

/**
 * Pure transformations for the temporary drag rows and the final note order.
 * This keeps list bookkeeping out of the Compose screen.
 */
internal data class DragRow(val notes: List<Note>, val key: String)

internal object NoteReorderModel {

    fun createDragRows(
        orderedNotes: List<Note>,
        movingIds: Set<Long>,
        grabbedNoteId: Long
    ): List<DragRow>? {
        val grabbedIndex = orderedNotes.indexOfFirst { it.id == grabbedNoteId }
        if (grabbedIndex < 0) return null

        val movingNotes = orderedNotes.filter { it.id in movingIds }
        if (movingNotes.isEmpty() || movingNotes.none { it.id == grabbedNoteId }) return null

        return buildList {
            orderedNotes.forEachIndexed { index, note ->
                if (index == grabbedIndex) {
                    // The group occupies the grabbed row's original slot.
                    add(DragRow(movingNotes, grabbedNoteId.toString()))
                }
                if (note.id !in movingIds) {
                    add(DragRow(listOf(note), note.id.toString()))
                }
            }
        }
    }

    fun afterId(rows: List<DragRow>, movingIds: Set<Long>): Long? {
        val movingIndex = rows.indexOfFirst { row -> row.notes.any { it.id in movingIds } }
        return if (movingIndex <= 0) null else rows[movingIndex - 1].notes.lastOrNull()?.id
    }

    /**
     * Inserts the moving notes after the requested row, without crossing the
     * pinned/unpinned partition. Mixed-pinned groups are intentionally rejected.
     */
    fun moveSelected(
        notes: List<Note>,
        movingIds: Set<Long>,
        afterId: Long?
    ): List<Note> {
        val ordered = notes.sortedByDescending { it.pinned }
        val moving = ordered.filter { it.id in movingIds }
        if (moving.isEmpty() || moving.map { it.pinned }.distinct().size != 1) return notes

        val remaining = ordered.filterNot { it.id in movingIds }
        val pinnedCount = remaining.count { it.pinned }
        val targetIndex = if (afterId == null) {
            0
        } else {
            val index = remaining.indexOfFirst { it.id == afterId }
            if (index < 0) remaining.size else index + 1
        }
        val insertAt = if (moving.first().pinned) {
            targetIndex.coerceIn(0, pinnedCount)
        } else {
            targetIndex.coerceIn(pinnedCount, remaining.size)
        }
        return remaining.toMutableList().apply { addAll(insertAt, moving) }
    }
}
