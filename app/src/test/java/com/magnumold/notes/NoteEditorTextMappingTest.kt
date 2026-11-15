package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteEditorTextMappingTest {

    @Test
    fun unchangedPlainTextRoundTripsChecklistBlocksAndIds() {
        val original = listOf(
            NoteBlock("First paragraph", id = "first"),
            NoteBlock("Buy milk", checked = true, isChecklist = true, id = "check")
        )

        val restored = original.withEditedPlainText(original.toPlainText())

        assertEquals(original, restored)
    }

    @Test
    fun editingParagraphsPreservesExistingBlockIdsAndChecklistState() {
        val original = listOf(
            NoteBlock("First paragraph", id = "first"),
            NoteBlock("Buy milk", checked = false, isChecklist = true, id = "check")
        )

        val edited = original.withEditedPlainText("Updated paragraph\n\n☑ Buy oat milk")

        assertEquals("first", edited[0].id)
        assertEquals("check", edited[1].id)
        assertEquals("Updated paragraph", edited[0].text)
        assertTrue(edited[1].isChecklist)
        assertTrue(edited[1].checked)
        assertEquals("Updated paragraph\n\n☑ Buy oat milk", edited.toPlainText())
    }

    @Test
    fun newlyAddedParagraphKeepsItsIdAcrossSubsequentEdits() {
        val original = listOf(NoteBlock("First", id = "first"))
        val withSecond = original.withEditedPlainText("First\n\nSecond")
        val secondId = withSecond[1].id

        val editedAgain = withSecond.withEditedPlainText("First\n\nUpdated second")

        assertEquals(secondId, editedAgain[1].id)
        assertEquals("Updated second", editedAgain[1].text)
    }

    @Test
    fun removingChecklistMarkerTurnsParagraphIntoPlainText() {
        val original = listOf(NoteBlock("Buy milk", isChecklist = true, id = "check"))

        val edited = original.withEditedPlainText("Buy milk")

        assertFalse(edited.single().isChecklist)
        assertEquals("Buy milk", edited.single().text)
    }

    @Test
    fun addingParagraphCreatesASeparateBlock() {
        val original = listOf(NoteBlock("First", id = "first"))

        val edited = original.withEditedPlainText("First\n\nSecond")

        assertEquals(2, edited.size)
        assertEquals("first", edited[0].id)
        assertEquals("Second", edited[1].text)
        assertEquals("First\n\nSecond", edited.toPlainText())
    }
}
