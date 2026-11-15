package com.magnumold.notes

import com.magnumold.notes.editor.engine.EditorSessionController
import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.model.HeadingBlock
import com.magnumold.notes.editor.model.InlineSpan
import com.magnumold.notes.editor.model.InlineStyle
import com.magnumold.notes.editor.model.ParagraphBlock
import com.magnumold.notes.editor.model.RichText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EditorSessionControllerTest {
    private fun document(vararg blocks: com.magnumold.notes.editor.model.DocumentBlock) =
        EditorDocument("session", createdAt = 1, updatedAt = 1, blocks = blocks.toList())

    @Test fun typingReplacesSelectionAndMovesCursor() {
        val paragraph = ParagraphBlock(content = RichText("Hello world"))
        val session = EditorSessionController(document(paragraph))
        session.activateBlock(paragraph.id)
        session.setSelection(6, 11)

        val state = session.replaceSelection("Notes")

        assertEquals("Hello Notes", (state.document.blocks.single() as ParagraphBlock).content.text)
        assertEquals(11, state.selection.anchor)
        assertEquals(11, state.selection.focus)
    }

    @Test fun reverseSelectionFormatsSameRange() {
        val paragraph = ParagraphBlock(content = RichText("abcdef"))
        val session = EditorSessionController(document(paragraph))
        session.activateBlock(paragraph.id)
        session.setSelection(5, 2)

        val state = session.applyInlineStyle(InlineStyle.BOLD, true)
        val edited = state.document.blocks.single() as ParagraphBlock

        assertEquals(listOf(InlineSpan(2, 5, setOf(InlineStyle.BOLD))), edited.content.spans)
        assertEquals(5, state.selection.anchor)
        assertEquals(2, state.selection.focus)
    }

    @Test fun selectionIsClampedAfterTextChangesAndUndoRestoresText() {
        val paragraph = ParagraphBlock(content = RichText("abc"))
        val session = EditorSessionController(document(paragraph))
        session.activateBlock(paragraph.id)
        session.setSelection(3, 3)
        session.replaceSelection("def")

        assertEquals("abcdef", (session.state.document.blocks.single() as ParagraphBlock).content.text)
        assertEquals("abc", (session.undo().document.blocks.single() as ParagraphBlock).content.text)
        assertEquals("abcdef", (session.redo().document.blocks.single() as ParagraphBlock).content.text)
    }

    @Test fun activateBlockMovesEditingFocusToThatBlock() {
        val first = ParagraphBlock(content = RichText("first"))
        val second = HeadingBlock(level = 2, content = RichText("second"))
        val session = EditorSessionController(document(first, second))

        val state = session.activateBlock(second.id)

        assertEquals(second.id, state.activeBlockId)
        assertEquals(6, state.selection.focus)
        assertNotNull(state.document.blocks.firstOrNull { it.id == first.id })
    }
}
