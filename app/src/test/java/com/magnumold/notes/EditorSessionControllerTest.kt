package com.magnumold.notes

import com.magnumold.notes.editor.engine.EditorSessionController
import com.magnumold.notes.editor.model.ChecklistBlock
import com.magnumold.notes.editor.model.ChecklistItem
import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.model.HeadingBlock
import com.magnumold.notes.editor.model.InlineSpan
import com.magnumold.notes.editor.model.InlineStyle
import com.magnumold.notes.editor.model.ListBlock
import com.magnumold.notes.editor.model.ListItem
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

    @Test fun nestedListItemCanBeEditedWithoutChangingSiblings() {
        val sibling = ListItem(content = RichText("sibling"))
        val nested = ListItem(content = RichText("child"))
        val root = ListItem(content = RichText("root"), children = listOf(nested))
        val list = ListBlock(ordered = false, items = listOf(root, sibling))
        val session = EditorSessionController(document(list))
        session.activateListItem(list.id, nested.id)
        session.setSelection(0, 5)

        val edited = session.replaceSelection("nested")
        val result = edited.document.blocks.single() as ListBlock

        assertEquals("nested", result.items.first().children.single().content.text)
        assertEquals("sibling", result.items.last().content.text)
        assertEquals(nested.id, edited.activeItemId)
        assertEquals("child", (session.undo().document.blocks.single() as ListBlock).items.first().children.single().content.text)
        assertEquals("nested", (session.redo().document.blocks.single() as ListBlock).items.first().children.single().content.text)
    }

    @Test fun nestedChecklistItemCanBeEditedAndKeepsCheckedState() {
        val nested = ChecklistItem(content = RichText("nested task"), checked = true)
        val root = ChecklistItem(content = RichText("root task"), children = listOf(nested))
        val checklist = ChecklistBlock(items = listOf(root))
        val session = EditorSessionController(document(checklist))
        session.activateChecklistItem(checklist.id, nested.id)
        session.setSelection(7, 11)

        val edited = session.replaceSelection("item")
        val result = edited.document.blocks.single() as ChecklistBlock
        val resultNested = result.items.single().children.single()

        assertEquals("nested item", resultNested.content.text)
        assertEquals(true, resultNested.checked)
        assertEquals("nested task", (session.undo().document.blocks.single() as ChecklistBlock).items.single().children.single().content.text)
    }

    @Test fun consecutiveTypingIsOneUndoStepAndCursorMovementSplitsGroups() {
        val paragraph = ParagraphBlock(content = RichText(""))
        val session = EditorSessionController(document(paragraph))
        session.activateBlock(paragraph.id)
        session.replaceSelection("a")
        session.replaceSelection("b")
        assertEquals("ab", (session.state.document.blocks.single() as ParagraphBlock).content.text)
        assertEquals("", (session.undo().document.blocks.single() as ParagraphBlock).content.text)
        assertEquals("ab", (session.redo().document.blocks.single() as ParagraphBlock).content.text)

        session.setSelection(2, 2)
        session.replaceSelection("c")
        session.setSelection(1, 1) // A real cursor move ends the current typing group.
        session.setSelection(3, 3)
        session.replaceSelection("d")
        assertEquals("abcd", (session.state.document.blocks.single() as ParagraphBlock).content.text)
        assertEquals("abc", (session.undo().document.blocks.single() as ParagraphBlock).content.text)
        assertEquals("ab", (session.undo().document.blocks.single() as ParagraphBlock).content.text)
    }
}
