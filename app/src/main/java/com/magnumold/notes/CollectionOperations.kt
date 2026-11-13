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
        val source = RecordListOperations.activeNotes(snapshot, sourceId)
        val moving = source.filter { it.id in ids }
        if (moving.isEmpty()) return snapshot
        val remaining = source.filterNot { it.id in ids }
        val withoutMoving = RecordListOperations.replace(snapshot, sourceId, remaining)
        return if (targetId == null) {
            withoutMoving.copy(notes = moving + withoutMoving.notes)
        } else {
            withoutMoving.copy(collections = withoutMoving.collections.map { collection ->
                if (collection.id == targetId) collection.copy(notes = moving + collection.notes) else collection
            })
        }
    }

}
