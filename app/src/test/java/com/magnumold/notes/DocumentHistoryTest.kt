package com.magnumold.notes

import com.magnumold.notes.editor.engine.DocumentCommand
import com.magnumold.notes.editor.engine.DocumentHistory
import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.model.ParagraphBlock
import com.magnumold.notes.editor.model.RichText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentHistoryTest {
    @Test fun insertCanBeUndoneAndRedone() {
        val initial = EditorDocument("history", createdAt = 1, updatedAt = 1, blocks = emptyList())
        val history = DocumentHistory(initial)
        val block = ParagraphBlock(content = RichText("Hello"))

        history.execute(DocumentCommand.Insert(0, block))
        assertEquals(listOf(block), history.current.blocks)

        assertEquals(initial.blocks, history.undo()?.blocks)
        assertEquals(listOf(block), history.redo()?.blocks)
        assertNull(history.redo())
    }

    @Test fun replaceAndMoveAreReversible() {
        val first = ParagraphBlock(content = RichText("A"))
        val second = ParagraphBlock(content = RichText("B"))
        val initial = EditorDocument("history-2", createdAt = 1, updatedAt = 1, blocks = listOf(first, second))
        val history = DocumentHistory(initial)

        history.execute(DocumentCommand.Replace(first.copy(content = RichText("Changed"))))
        assertEquals("Changed", (history.current.blocks[0] as ParagraphBlock).content.text)
        assertEquals(initial.blocks, history.undo()?.blocks)

        history.execute(DocumentCommand.Move(first.id, 1))
        assertEquals(listOf(second.id, first.id), history.current.blocks.map { it.id })
        assertEquals(initial.blocks.map { it.id }, history.undo()?.blocks?.map { it.id })
    }
}
