package com.magnumold.notes

import com.magnumold.notes.editor.migration.LegacyNoteDocumentMigrator
import com.magnumold.notes.editor.model.ChecklistBlock
import com.magnumold.notes.editor.model.ParagraphBlock
import com.magnumold.notes.editor.model.plainText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LegacyNoteDocumentMigratorTest {
    @Test fun preservesParagraphsChecklistStateAndTimestamps() {
        val note = Note(
            id = 42L, title = "", updated = 200L, created = 100L,
            blocks = listOf(
                NoteBlock("First paragraph", id = "p-1"),
                NoteBlock("Remember this", checked = true, isChecklist = true, id = "c-1")
            )
        )

        val migrated = LegacyNoteDocumentMigrator.migrate(note)

        assertEquals("42", migrated.id)
        assertEquals(100L, migrated.createdAt)
        assertEquals(200L, migrated.updatedAt)
        assertEquals("First paragraph\n\n☑ Remember this", migrated.plainText())
        assertFalse((migrated.blocks[0] as ParagraphBlock).content.text.isEmpty())
        assertEquals(true, (migrated.blocks[1] as ChecklistBlock).items.single().checked)
    }

    @Test(expected = IllegalArgumentException::class)
    fun doesNotMigrateTaskRecords() {
        LegacyNoteDocumentMigrator.migrate(
            Note(1L, "", listOf(NoteBlock("Task")), 2L, type = RecordType.TASK)
        )
    }
}
