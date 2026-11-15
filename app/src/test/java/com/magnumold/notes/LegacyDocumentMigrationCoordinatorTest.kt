package com.magnumold.notes

import android.content.Context
import com.magnumold.notes.editor.migration.LegacyDocumentMigrationCoordinator
import com.magnumold.notes.editor.migration.MigrationBackupStore
import com.magnumold.notes.editor.model.plainText
import com.magnumold.notes.editor.storage.DocumentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LegacyDocumentMigrationCoordinatorTest {
    private fun context() = RuntimeEnvironment.getApplication() as Context

    private fun note(id: Long, text: String, type: RecordType = RecordType.NOTE) = Note(
        id = id,
        title = "",
        blocks = listOf(NoteBlock(text, id = "block-$id")),
        updated = id + 100,
        created = id,
        type = type
    )

    @Test
    fun backsUpThenMigratesOrdinaryNotesAcrossAllLegacyLocations() {
        val context = context()
        java.io.File(context.filesDir, "migration-documents-test").deleteRecursively()
        java.io.File(context.filesDir, "migration-backup-test").deleteRecursively()
        val snapshot = PersistenceSnapshot(
            notes = listOf(note(1, "Active"), note(2, "Task", RecordType.TASK)),
            collections = listOf(NoteCollection(10, "Saved", listOf(note(3, "Collection")))),
            archived = listOf(VaultNote(note(4, "Archived"), null, 0, 10)),
            trash = listOf(VaultNote(note(5, "Trashed"), null, 0, 11))
        )
        val repository = DocumentRepository(context, "migration-documents-test")
        val backupStore = MigrationBackupStore(context, "migration-backup-test")

        val report = LegacyDocumentMigrationCoordinator(repository, backupStore).migrate(snapshot)

        assertEquals(4, report.migratedCount)
        assertEquals(0, report.alreadyMigratedCount)
        assertEquals(listOf("1", "3", "4", "5"), report.documentIds)
        assertEquals("Active", repository.load("1").getOrNull()?.plainText())
        assertEquals("Collection", repository.load("3").getOrNull()?.plainText())
        assertEquals("Archived", repository.load("4").getOrNull()?.plainText())
        assertEquals("Trashed", repository.load("5").getOrNull()?.plainText())
        assertTrue(repository.load("2").getOrNull() == null)
        assertEquals(report.backup, backupStore.readVerifiedBackup())
        assertEquals(snapshot.notes.single().id, parseBackupJson(backupStore.readBackupPayload()).notes.first { it.id == 1L }.id)
    }

    @Test
    fun migrationIsIdempotentAndNeverOverwritesAnExistingRichDocument() {
        val context = context()
        java.io.File(context.filesDir, "migration-idempotent-docs").deleteRecursively()
        java.io.File(context.filesDir, "migration-idempotent-backup").deleteRecursively()
        val snapshot = PersistenceSnapshot(
            notes = listOf(note(21, "Legacy")),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList()
        )
        val repository = DocumentRepository(context, "migration-idempotent-docs")
        val coordinator = LegacyDocumentMigrationCoordinator(
            repository,
            MigrationBackupStore(context, "migration-idempotent-backup")
        )

        val first = coordinator.migrate(snapshot)
        val second = coordinator.migrate(snapshot)

        assertEquals(1, first.migratedCount)
        assertEquals(0, second.migratedCount)
        assertEquals(1, second.alreadyMigratedCount)
        assertEquals(first.backup, second.backup)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateLegacyIdsAbortAfterBackupWithoutWritingDocuments() {
        val context = context()
        java.io.File(context.filesDir, "migration-duplicate-docs").deleteRecursively()
        java.io.File(context.filesDir, "migration-duplicate-backup").deleteRecursively()
        val snapshot = PersistenceSnapshot(
            notes = listOf(note(31, "First"), note(31, "Second")),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList()
        )
        val repository = DocumentRepository(context, "migration-duplicate-docs")
        val backup = MigrationBackupStore(context, "migration-duplicate-backup")

        LegacyDocumentMigrationCoordinator(repository, backup).migrate(snapshot)
    }
}
