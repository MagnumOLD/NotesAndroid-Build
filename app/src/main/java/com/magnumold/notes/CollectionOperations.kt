package com.magnumold.notes

/**
 * Pure snapshot transformations for collection and active-list operations.
 * These functions keep list mutation rules out of the Compose coordinator.
 */
internal object CollectionOperations {
    fun create(snapshot: PersistenceSnapshot, id: Long, name: String): PersistenceSnapshot =
        snapshot.copy(collections = snapshot.collections + NoteCollection(id, name))

    fun rename(snapshot: PersistenceSnapshot, id: Long, name: String): PersistenceSnapshot =
        snapshot.copy(collections = snapshot.collections.map { collection ->
            if (collection.id == id) collection.copy(name = name) else collection
        })

    fun delete(snapshot: PersistenceSnapshot, id: Long): PersistenceSnapshot {
        val collection = snapshot.collections.firstOrNull { it.id == id } ?: return snapshot
        return snapshot.copy(
            notes = collection.notes + snapshot.notes,
            collections = snapshot.collections.filterNot { it.id == id }
        )
    }

    fun move(snapshot: PersistenceSnapshot, id: Long, delta: Int): PersistenceSnapshot {
        val from = snapshot.collections.indexOfFirst { it.id == id }
        if (from < 0 || snapshot.collections.isEmpty()) return snapshot
        val to = (from + delta).coerceIn(0, snapshot.collections.lastIndex)
        if (from == to) return snapshot
        return snapshot.copy(collections = snapshot.collections.toMutableList().apply {
            add(to, removeAt(from))
        })
    }

    fun transfer(
        snapshot: PersistenceSnapshot,
        sourceId: Long?,
        targetId: Long?,
        ids: Set<Long>
    ): PersistenceSnapshot {
        if (sourceId == targetId || ids.isEmpty()) return snapshot
        val source = activeNotes(snapshot, sourceId)
        val moving = source.filter { it.id in ids }
        if (moving.isEmpty()) return snapshot
        val remaining = source.filterNot { it.id in ids }
        val withoutMoving = replaceActiveNotes(snapshot, sourceId, remaining)
        return if (targetId == null) {
            withoutMoving.copy(notes = moving + withoutMoving.notes)
        } else {
            withoutMoving.copy(collections = withoutMoving.collections.map { collection ->
                if (collection.id == targetId) collection.copy(notes = moving + collection.notes) else collection
            })
        }
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

    fun replaceActiveNotes(
        snapshot: PersistenceSnapshot,
        collectionId: Long?,
        notes: List<Note>
    ): PersistenceSnapshot =
        if (collectionId == null) snapshot.copy(notes = notes)
        else snapshot.copy(collections = snapshot.collections.map { collection ->
            if (collection.id == collectionId) collection.copy(notes = notes) else collection
        })
}
