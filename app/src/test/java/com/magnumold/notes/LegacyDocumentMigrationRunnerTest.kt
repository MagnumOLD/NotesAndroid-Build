package com.magnumold.notes

import android.content.Context
import com.magnumold.notes.editor.migration.LegacyDocumentMigrationCoordinator
import com.magnumold.notes.editor.migration.LegacyDocumentMigrationRunner
import com.magnumold.notes.editor.migration.MigrationBackupStore
import com.magnumold.notes.editor.storage.DocumentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LegacyDocumentMigrationRunnerTest {
    @Test
    fun writesCompletionMarkerOnlyAfterSuccessfulMigrationAndSkipsLaterRuns() {
        val context = RuntimeEnvironment.getApplication() as Context
        listOf("migration-runner-docs", "migration-runner-backup").forEach {
            java.io.File(context.filesDir, it).deleteRecursively()
        }
        val prefs = context.getSharedPreferences("migration-runner-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val repository = DocumentRepository(context, "migration-runner-docs")
        val coordinator = LegacyDocumentMigrationCoordinator(
            repository,
            MigrationBackupStore(context, "migration-runner-backup")
        )
        val runner = LegacyDocumentMigrationRunner(prefs, coordinator)
        val snapshot = PersistenceSnapshot(
            notes = listOf(Note(51, "", listOf(NoteBlock("Legacy")), 151)),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList()
        )

        assertFalse(runner.isComplete)
        val first = runner.runIfNeeded(snapshot)
        assertTrue(runner.isComplete)
        assertEquals(1, first?.migratedCount)

        val second = runner.runIfNeeded(snapshot.copy(
            notes = snapshot.notes + Note(52, "", listOf(NoteBlock("Later")), 152)
        ))
        assertNull(second)
        assertNull(repository.load("52").getOrNull())
    }
}
