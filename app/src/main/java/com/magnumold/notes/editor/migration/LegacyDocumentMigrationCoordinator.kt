package com.magnumold.notes.editor.migration

import com.magnumold.notes.Note
import com.magnumold.notes.PersistenceSnapshot
import com.magnumold.notes.RecordType
import com.magnumold.notes.TimeTrackingState
import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.model.plainText
import com.magnumold.notes.editor.storage.DocumentRepository

internal data class LegacyDocumentMigrationReport(
    val migratedCount: Int,
    val alreadyMigratedCount: Int,
    val documentIds: List<String>,
    val backup: MigrationBackupInfo
)

/**
 * Idempotent migration gate. It writes no legacy records and refuses to replace
 * any document that differs from the expected legacy conversion.
 */
internal class LegacyDocumentMigrationCoordinator(
    private val repository: DocumentRepository,
    private val backupStore: MigrationBackupStore
) {
    fun migrate(
        snapshot: PersistenceSnapshot,
        timeTracking: TimeTrackingState = TimeTrackingState()
    ): LegacyDocumentMigrationReport {
        // The backup is deliberately the first durable operation.
        val backup = backupStore.ensureBackup(snapshot, timeTracking)
        val candidates = collectNotes(snapshot).filter { it.type == RecordType.NOTE }
        val duplicateIds = candidates.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicateIds.isEmpty()) {
            "Duplicate legacy note ids prevent safe migration: ${duplicateIds.joinToString()}"
        }

        val expected = candidates.map(LegacyNoteDocumentMigrator::migrate)
        var migratedCount = 0
        var alreadyMigratedCount = 0

        expected.forEach { document ->
            val existing = repository.load(document.id).getOrElse { error ->
                throw IllegalStateException("Cannot read existing document ${document.id}", error)
            }
            when {
                existing == null -> {
                    repository.save(document)
                    migratedCount++
                }
                existing == document -> alreadyMigratedCount++
                else -> throw IllegalStateException(
                    "Document ${document.id} already exists and differs from the legacy conversion"
                )
            }
        }

        val verified = expected.map { expectedDocument ->
            repository.load(expectedDocument.id).getOrElse { error ->
                throw IllegalStateException("Cannot verify migrated document ${expectedDocument.id}", error)
            } ?: error("Migrated document ${expectedDocument.id} is missing")
        }
        require(verified == expected) { "Migrated document validation failed" }
        require(verified.map { it.plainText() } == expected.map { it.plainText() }) {
            "Migrated document text validation failed"
        }

        return LegacyDocumentMigrationReport(
            migratedCount = migratedCount,
            alreadyMigratedCount = alreadyMigratedCount,
            documentIds = expected.map { it.id },
            backup = backup
        )
    }

    private fun collectNotes(snapshot: PersistenceSnapshot): List<Note> {
        val roots = buildList {
            addAll(snapshot.notes)
            snapshot.collections.forEach { addAll(it.notes) }
            snapshot.archived.forEach { add(it.note) }
            snapshot.trash.forEach { add(it.note) }
        }
        val result = mutableListOf<Note>()
        fun visit(note: Note) {
            result += note
            note.children.forEach(::visit)
        }
        roots.forEach(::visit)
        return result
    }
}
