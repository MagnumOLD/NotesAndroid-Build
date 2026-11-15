package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupJsonCodecTest {
    private fun note(id: Long, text: String, children: List<Note> = emptyList()) = Note(
        id = id,
        title = "",
        blocks = listOf(NoteBlock(text)),
        updated = 10L,
        created = 5L,
        children = children
    )

    @Test
    fun preservesDifferentNotesEvenWhenTheirTextIsIdentical() {
        val existing = note(1L, "Same text")
        val imported = note(2L, "Same text")

        val merged = mergeImportedNotes(listOf(existing), listOf(imported))

        assertEquals(listOf(1L, 2L), merged.map { it.id })
    }

    @Test
    fun mergesRepeatedIdsAndKeepsTheirNestedChildren() {
        val existing = note(1L, "Parent", listOf(note(2L, "First child")))
        val imported = note(1L, "Updated parent", listOf(note(3L, "Second child")))

        val merged = mergeImportedNotes(listOf(existing), listOf(imported))

        assertEquals(1, merged.size)
        assertEquals(setOf(2L, 3L), merged.single().children.map { it.id }.toSet())
    }

    @Test
    fun remapsLegacyIdsAcrossActiveCollectionsArchiveTrashAndChildren() {
        val data = BackupData(
            notes = listOf(note(1L, "Active", listOf(note(2L, "Nested")))),
            collections = listOf(NoteCollection(10L, "Collection", listOf(note(3L, "Saved")))),
            archived = listOf(VaultNote(note(4L, "Archived"), null, 0, 10L)),
            trash = listOf(VaultNote(note(5L, "Trashed"), null, 0, 11L))
        )

        val remapped = data.remapNoteIds(mapOf("1" to "101", "2" to "102", "3" to "103", "4" to "104", "5" to "105"))

        assertEquals(101L, remapped.notes.single().id)
        assertEquals(102L, remapped.notes.single().children.single().id)
        assertEquals(103L, remapped.collections.single().notes.single().id)
        assertEquals(104L, remapped.archived.single().note.id)
        assertEquals(105L, remapped.trash.single().note.id)
    }
}
