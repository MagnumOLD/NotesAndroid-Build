package com.magnumold.notes

/**
 * Immutable transformations for records inside the main list or a collection.
 */
internal object RecordListOperations {
    fun create(
        snapshot: PersistenceSnapshot,
        collectionId: Long?,
        note: Note
    ): PersistenceSnapshot {
        val current = activeNotes(snapshot, collectionId)
        return replace(snapshot, collectionId, listOf(note) + current)
    }

    fun update(
        snapshot: PersistenceSnapshot,
        collectionId: Long?,
        note: Note
    ): PersistenceSnapshot {
        val current = activeNotes(snapshot, collectionId)
        val index = current.indexOfFirst { it.id == note.id }
        if (index < 0) return snapshot
        return replace(snapshot, collectionId, current.toMutableList().apply {
            this[index] = note
        })
    }

    fun remove(
        snapshot: PersistenceSnapshot,
        collectionId: Long?,
        noteId: Long
    ): PersistenceSnapshot {
        val current = activeNotes(snapshot, collectionId)
        val remaining = current.filterNot { it.id == noteId }
        return if (remaining == current) snapshot else replace(snapshot, collectionId, remaining)
    }

    fun reorder(
        snapshot: PersistenceSnapshot,
        collectionId: Long?,
        ids: Set<Long>,
        afterId: Long?
    ): PersistenceSnapshot {
        val current = activeNotes(snapshot, collectionId)
        val reordered = NoteReorderModel.moveSelected(current, ids, afterId)
        return if (reordered == current) snapshot else replaceActiveNotes(snapshot, collectionId, reordered)
    }

    fun togglePinned(
        snapshot: PersistenceSnapshot,
        collectionId: Long?,
        ids: Set<Long>
    ): PersistenceSnapshot {
        val current = activeNotes(snapshot, collectionId)
        val allPinned = current.filter { it.id in ids }.all { it.pinned }
        val updated = current.map { if (it.id in ids) it.copy(pinned = !allPinned) else it }
        return replaceActiveNotes(snapshot, collectionId, updated.sortedByDescending { it.pinned })
    }

    fun changeColor(
        snapshot: PersistenceSnapshot,
        collectionId: Long?,
        noteId: Long,
        colorIndex: Int
    ): PersistenceSnapshot {
        val current = activeNotes(snapshot, collectionId)
        val index = current.indexOfFirst { it.id == noteId }
        if (index < 0) return snapshot
        return replaceActiveNotes(snapshot, collectionId, current.toMutableList().apply {
            this[index] = this[index].copy(colorIndex = colorIndex)
        })
    }

    fun activeNotes(snapshot: PersistenceSnapshot, collectionId: Long?): List<Note> =
        if (collectionId == null) snapshot.notes
        else snapshot.collections.firstOrNull { it.id == collectionId }?.notes.orEmpty()

    fun replace(
        snapshot: PersistenceSnapshot,
        collectionId: Long?,
        notes: List<Note>
    ): PersistenceSnapshot =
        if (collectionId == null) snapshot.copy(notes = notes)
        else snapshot.copy(collections = snapshot.collections.map { collection ->
            if (collection.id == collectionId) collection.copy(notes = notes) else collection
        })
}
