package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordOperationsTest {
    private fun note(id: Long, text: String, children: List<Note> = emptyList()) = Note(
        id = id,
        title = text,
        blocks = listOf(NoteBlock(text = text)),
        updated = id,
        children = children
    )

    private fun vault(note: Note, movedAt: Long = 1L) =
        VaultNote(note = note, collectionId = null, position = 0, movedAt = movedAt)

    @Test
    fun importingBackupMergesArchiveAndTrashWithoutDuplicatingExistingEntries() {
        val existingArchive = vault(note(10L, "existing archive"))
        val existingTrash = vault(note(20L, "existing trash"))
        val importedArchive = vault(note(30L, "imported archive"))
        val importedTrash = vault(note(40L, "imported trash"))
        val current = PersistenceSnapshot(
            notes = emptyList(),
            collections = emptyList(),
            archived = listOf(existingArchive),
            trash = listOf(existingTrash)
        )
        val backup = BackupData(
            notes = emptyList(),
            collections = emptyList(),
            archived = listOf(importedArchive),
            trash = listOf(importedTrash)
        )

        val merged = RecordOperations.mergeBackup(current, backup)

        assertEquals(listOf(10L, 30L), merged.archived.map { it.note.id }.sorted())
        assertEquals(listOf(20L, 40L), merged.trash.map { it.note.id }.sorted())
        assertEquals(1, merged.archived.count { it.note.id == existingArchive.note.id })
        assertEquals(1, merged.trash.count { it.note.id == existingTrash.note.id })
    }

    @Test
    fun importingSameBackupTwiceIsIdempotentForVaultEntries() {
        val current = PersistenceSnapshot(
            notes = emptyList(),
            collections = emptyList(),
            archived = listOf(vault(note(10L, "old archive"))),
            trash = listOf(vault(note(20L, "old trash")))
        )
        val backup = BackupData(
            notes = emptyList(),
            collections = emptyList(),
            archived = listOf(vault(note(30L, "new archive"))),
            trash = listOf(vault(note(40L, "new trash")))
        )

        val once = RecordOperations.mergeBackup(current, backup)
        val twice = RecordOperations.mergeBackup(once, backup)

        assertEquals(once.archived.map { it.note.id }.toSet(), twice.archived.map { it.note.id }.toSet())
        assertEquals(once.trash.map { it.note.id }.toSet(), twice.trash.map { it.note.id }.toSet())
        assertTrue(twice.archived.size == 2 && twice.trash.size == 2)
    }
    private fun allIds(snapshot: PersistenceSnapshot): List<Long> {
        val ids = mutableListOf<Long>()
        fun collect(note: Note) {
            ids += note.id
            note.children.forEach(::collect)
        }
        snapshot.notes.forEach(::collect)
        snapshot.collections.forEach { it.notes.forEach(::collect) }
        snapshot.archived.forEach { collect(it.note) }
        snapshot.trash.forEach { collect(it.note) }
        return ids
    }

    @Test
    fun backupImportRemapsCollisionsAcrossActiveCollectionsArchiveAndTrash() {
        val current = PersistenceSnapshot(
            notes = listOf(note(10L, "Active")),
            collections = listOf(NoteCollection(1L, "Saved", listOf(note(20L, "Collection")))),
            archived = listOf(vault(note(30L, "Archived"))),
            trash = emptyList()
        )
        val backup = BackupData(
            notes = emptyList(),
            collections = emptyList(),
            archived = listOf(vault(note(10L, "Different archived record"))),
            trash = listOf(vault(note(20L, "Different trashed record")))
        )

        val merged = RecordOperations.mergeBackup(current, backup)

        assertEquals(2, merged.archived.size + merged.trash.size)
        assertTrue(merged.archived.single().note.id != 10L)
        assertTrue(merged.trash.single().note.id != 20L)
        assertEquals(allIds(merged).size, allIds(merged).toSet().size)
    }

    @Test
    fun backupImportRemapsNestedIdsAgainstEverySection() {
        val current = PersistenceSnapshot(
            notes = listOf(note(1L, "Active parent", listOf(note(2L, "Active child")))),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList()
        )
        val backup = BackupData(
            notes = emptyList(),
            collections = listOf(
                NoteCollection(7L, "Imported", listOf(note(3L, "Collection parent", listOf(note(2L, "Conflicting child")))))
            ),
            archived = emptyList(),
            trash = emptyList()
        )

        val merged = RecordOperations.mergeBackup(current, backup)

        val importedChildId = merged.collections.single().notes.single().children.single().id
        assertTrue(importedChildId != 2L)
        assertEquals(allIds(merged).size, allIds(merged).toSet().size)
    }

    @Test
    fun repeatedCrossSectionCollisionImportIsIdempotent() {
        val current = PersistenceSnapshot(
            notes = listOf(note(100L, "Existing active")),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList()
        )
        val backup = BackupData(
            notes = emptyList(),
            collections = emptyList(),
            archived = listOf(vault(note(100L, "Imported archived copy"))),
            trash = emptyList()
        )

        val once = RecordOperations.mergeBackup(current, backup)
        val twice = RecordOperations.mergeBackup(once, backup)

        assertEquals(2, once.notes.size + once.archived.size)
        assertEquals(once.archived.map { it.note.id }, twice.archived.map { it.note.id })
        assertEquals(allIds(once).toSet(), allIds(twice).toSet())
        assertEquals(allIds(twice).size, allIds(twice).toSet().size)
    }

    @Test
    fun remappedTaskIdsAlsoRemapImportedTimeTrackingReferences() {
        val current = PersistenceSnapshot(
            notes = listOf(note(40L, "Existing note 40"), note(41L, "Existing note 41")),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList()
        )
        val importedTask = note(
            40L,
            "Parent task",
            listOf(note(41L, "Child task").copy(type = RecordType.TASK))
        ).copy(type = RecordType.TASK)
        val backup = BackupData(
            notes = listOf(importedTask),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList()
        )
        val tracking = TimeTrackingState(
            sessions = listOf(
                WorkSession("s1", 41L, 40L, "Child task", null, 1_000L, 2_000L, 1_000L, WorkSessionEndReason.STOP)
            ),
            activeSessions = mapOf(
                41L to ActiveWorkSession("a1", 41L, 40L, "Child task", null, 3_000L)
            ),
            protectionTimer = ProtectionTimer(
                taskId = 41L,
                parentTaskId = 40L,
                taskTitle = "Child task",
                subtaskTitle = null,
                startedAtMillis = 4_000L,
                endsAtMillis = 5_000L,
                endMode = ProtectionEndMode.SIGNAL_ONLY
            )
        )

        val merge = RecordOperations.mergeBackupWithReport(current, backup)
        val repeatedMerge = RecordOperations.mergeBackupWithReport(merge.snapshot, backup)
        val remappedTracking = tracking.remapTaskIds(merge.taskIdRemaps)

        assertEquals(merge.taskIdRemaps, repeatedMerge.taskIdRemaps)
        assertEquals(allIds(merge.snapshot).toSet(), allIds(repeatedMerge.snapshot).toSet())
        assertTrue(merge.taskIdRemaps.containsKey(40L))
        assertTrue(merge.taskIdRemaps.containsKey(41L))
        assertEquals(merge.taskIdRemaps.getValue(41L), remappedTracking.sessions.single().taskId)
        assertEquals(merge.taskIdRemaps.getValue(40L), remappedTracking.sessions.single().parentTaskId)
        assertEquals(merge.taskIdRemaps.getValue(41L), remappedTracking.activeSessions.keys.single())
        assertEquals(merge.taskIdRemaps.getValue(41L), remappedTracking.protectionTimer?.taskId)
        assertEquals(merge.taskIdRemaps.getValue(40L), remappedTracking.protectionTimer?.parentTaskId)
    }

}
