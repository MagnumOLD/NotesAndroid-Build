package com.magnumold.notes

internal object RecordOperations {
    fun recordCount(snapshot: PersistenceSnapshot): Int =
        snapshot.notes.size + snapshot.collections.sumOf { it.notes.size } +
            snapshot.archived.size + snapshot.trash.size

    fun mergeBackup(current: PersistenceSnapshot, imported: BackupData): PersistenceSnapshot =
        current.copy(
            notes = mergeImportedNotes(current.notes, imported.notes),
            collections = mergeImportedCollections(current.collections, imported.collections),
            archived = mergeImportedVault(current.archived, imported.archived),
            trash = mergeImportedVault(current.trash, imported.trash)
        )

    fun restoreVaultEntry(
        snapshot: PersistenceSnapshot,
        entry: VaultNote,
        fromArchive: Boolean
    ): PersistenceSnapshot {
        val source = if (fromArchive) snapshot.archived else snapshot.trash
        if (source.none { it.note.id == entry.note.id }) return snapshot
        val withoutEntry = if (fromArchive) snapshot.copy(
            archived = snapshot.archived.filterNot { it.note.id == entry.note.id }
        ) else snapshot.copy(
            trash = snapshot.trash.filterNot { it.note.id == entry.note.id }
        )
        val collectionIndex = entry.collectionId?.let { id ->
            withoutEntry.collections.indexOfFirst { it.id == id }
        } ?: -1
        return if (collectionIndex >= 0) {
            val collections = withoutEntry.collections.toMutableList()
            val target = collections[collectionIndex]
            val notes = target.notes.toMutableList()
            notes.add(entry.position.coerceIn(0, notes.size), entry.note)
            collections[collectionIndex] = target.copy(notes = notes)
            withoutEntry.copy(collections = collections)
        } else {
            val notes = withoutEntry.notes.toMutableList()
            notes.add(entry.position.coerceIn(0, notes.size), entry.note)
            withoutEntry.copy(notes = notes)
        }
    }

    fun moveArchivedToTrash(snapshot: PersistenceSnapshot, entry: VaultNote): PersistenceSnapshot {
        if (snapshot.archived.none { it.note.id == entry.note.id }) return snapshot
        return snapshot.copy(
            archived = snapshot.archived.filterNot { it.note.id == entry.note.id },
            trash = listOf(entry) + snapshot.trash
        )
    }

    fun deleteFromTrash(snapshot: PersistenceSnapshot, entry: VaultNote): PersistenceSnapshot =
        snapshot.copy(trash = snapshot.trash.filterNot { it.note.id == entry.note.id })

    fun moveActiveRecordsToVault(
        snapshot: PersistenceSnapshot,
        collectionId: Long?,
        ids: Set<Long>,
        toTrash: Boolean,
        now: Long = System.currentTimeMillis()
    ): PersistenceSnapshot {
        if (ids.isEmpty()) return snapshot
        val active = if (collectionId == null) snapshot.notes
            else snapshot.collections.firstOrNull { it.id == collectionId }?.notes ?: return snapshot
        val moving = active.filter { it.id in ids }
        if (moving.isEmpty()) return snapshot
        val entries = moving.map { note ->
            VaultNote(note, collectionId, active.indexOfFirst { it.id == note.id }, now)
        }
        val remaining = active.filterNot { it.id in ids }
        val updated = if (collectionId == null) snapshot.copy(notes = remaining)
        else snapshot.copy(collections = snapshot.collections.map { collection ->
            if (collection.id == collectionId) collection.copy(notes = remaining) else collection
        })
        return if (toTrash) updated.copy(trash = entries.asReversed() + updated.trash)
        else updated.copy(archived = entries.asReversed() + updated.archived)
    }
}
