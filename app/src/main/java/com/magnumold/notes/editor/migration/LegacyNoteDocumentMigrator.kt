package com.magnumold.notes.editor.migration

import com.magnumold.notes.Note
import com.magnumold.notes.RecordType
import com.magnumold.notes.editor.model.ChecklistBlock
import com.magnumold.notes.editor.model.ChecklistItem
import com.magnumold.notes.editor.model.DocumentMetadata
import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.model.ParagraphBlock
import com.magnumold.notes.editor.model.RichText
import com.magnumold.notes.editor.model.newBlockId

/**
 * One-way converter for legacy ordinary notes.
 * It deliberately does not migrate TASK or PURCHASE records: those belong to their own editors.
 */
internal object LegacyNoteDocumentMigrator {
    fun migrate(note: Note): EditorDocument {
        require(note.type == RecordType.NOTE) { "Only ordinary notes can be migrated to documents" }

        val usedIds = mutableSetOf<String>()
        fun stableId(candidate: String): String {
            val preferred = candidate.takeIf(String::isNotBlank) ?: newBlockId()
            if (usedIds.add(preferred)) return preferred
            val replacement = newBlockId()
            usedIds.add(replacement)
            return replacement
        }

        val blocks = note.blocks.map { legacy ->
            val id = stableId(legacy.id)
            if (legacy.isChecklist) {
                ChecklistBlock(id, listOf(ChecklistItem(content = RichText(legacy.text), checked = legacy.checked)))
            } else {
                ParagraphBlock(id, RichText(legacy.text))
            }
        }.ifEmpty { listOf(ParagraphBlock(content = RichText(""))) }

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
