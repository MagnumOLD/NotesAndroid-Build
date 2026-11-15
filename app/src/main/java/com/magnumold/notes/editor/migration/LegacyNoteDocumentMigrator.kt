package com.magnumold.notes.editor.migration

import com.magnumold.notes.Note
import com.magnumold.notes.RecordType
import com.magnumold.notes.editor.model.ChecklistBlock
import com.magnumold.notes.editor.model.ChecklistItem
import com.magnumold.notes.editor.model.DocumentMetadata
import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.model.ParagraphBlock
import com.magnumold.notes.editor.model.RichText

/**
 * One-way converter for legacy ordinary notes.
 * It deliberately does not migrate TASK or PURCHASE records: those belong to their own editors.
 */
internal object LegacyNoteDocumentMigrator {
    fun migrate(note: Note): EditorDocument {
        require(note.type == RecordType.NOTE) { "Only ordinary notes can be migrated to documents" }

        val usedIds = mutableSetOf<String>()

        val blocks = note.blocks.mapIndexed { index, legacy ->
            val candidate = legacy.id.takeIf(String::isNotBlank)
                ?: "legacy-${note.id}-block-$index"
            val id = if (usedIds.add(candidate)) candidate else {
                var replacement = java.util.UUID.nameUUIDFromBytes(
                    "${note.id}:$index:$candidate".toByteArray(Charsets.UTF_8)
                ).toString()
                var suffix = 0
                while (!usedIds.add(replacement)) {
                    suffix++
                    replacement = java.util.UUID.nameUUIDFromBytes(
                        "${note.id}:$index:$candidate:$suffix".toByteArray(Charsets.UTF_8)
                    ).toString()
                }
                replacement
            }
            if (legacy.isChecklist) {
                ChecklistBlock(id, listOf(ChecklistItem(content = RichText(legacy.text), checked = legacy.checked)))
            } else {
                ParagraphBlock(id, RichText(legacy.text))
            }
        }.ifEmpty {
            val emptyId = java.util.UUID.nameUUIDFromBytes(
                "legacy-${note.id}-empty-document".toByteArray(Charsets.UTF_8)
            ).toString()
            listOf(ParagraphBlock(id = emptyId, content = RichText("")))
        }

        return EditorDocument(
            id = note.id.toString(),
            createdAt = note.created,
            updatedAt = note.updated,
            metadata = DocumentMetadata(
                title = if (note.titleEnabled) note.titleText else note.title,
                tags = emptyList()
            ),
            blocks = blocks
        )
    }
}
