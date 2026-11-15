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
    fun insertingParagraphAtBeginningDoesNotShiftExistingBlockIds() {
        val original = listOf(
            NoteBlock("Alpha", id = "alpha"),
            NoteBlock("Beta", id = "beta"),
            NoteBlock("Gamma", id = "gamma")
        )

        val edited = original.withEditedPlainText("New paragraph\n\nAlpha\n\nBeta\n\nGamma")

        assertEquals("alpha", edited[1].id)
        assertEquals("beta", edited[2].id)
        assertEquals("gamma", edited[3].id)
    }

    @Test
    fun insertingParagraphInMiddleDoesNotStealFollowingBlockId() {
        val original = listOf(
            NoteBlock("Alpha", id = "alpha"),
            NoteBlock("Beta", id = "beta"),
            NoteBlock("Gamma", id = "gamma")
        )

        val edited = original.withEditedPlainText("Alpha\n\nNew paragraph\n\nBeta\n\nGamma")

        assertEquals("alpha", edited[0].id)
        assertEquals("beta", edited[2].id)
        assertEquals("gamma", edited[3].id)
        assertTrue(edited[1].id != "beta")
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

    @Test
    fun wordTypingCreatesSeparateUndoGroupsAtWhitespaceBoundary() {
        val first = describeTextEdit("", "П", 0, 0)!!
        val firstDecision = decideUndoHistoryAction(UndoGroupingState(), first)
        assertEquals(UndoHistoryAction.APPEND, firstDecision.action)

        val nextLetter = describeTextEdit("П", "Пр", 1, 1)!!
        val secondDecision = decideUndoHistoryAction(firstDecision.nextState, nextLetter)
        assertEquals(UndoHistoryAction.REPLACE_CURRENT, secondDecision.action)

        val word = "Привет"
        val space = describeTextEdit(word, "$word ", word.length, word.length)!!
        val spaceDecision = decideUndoHistoryAction(secondDecision.nextState, space)
        assertEquals(UndoHistoryAction.SKIP, spaceDecision.action)

        val nextWord = describeTextEdit("$word ", "$word м", word.length + 1, word.length + 1)!!
        val nextWordDecision = decideUndoHistoryAction(spaceDecision.nextState, nextWord)
        assertEquals(UndoHistoryAction.APPEND, nextWordDecision.action)

        val nextWordLetter = describeTextEdit("$word м", "$word ми", word.length + 2, word.length + 2)!!
        assertEquals(
            UndoHistoryAction.REPLACE_CURRENT,
            decideUndoHistoryAction(nextWordDecision.nextState, nextWordLetter).action
        )
    }

    @Test
    fun punctuationEndsTheCurrentWordUndoGroup() {
        val hello = describeTextEdit("Приве", "Привет", 5, 5)!!
        val helloDecision = decideUndoHistoryAction(UndoGroupingState(), hello)

        val comma = describeTextEdit("Привет", "Привет,", 6, 6)!!
        val commaDecision = decideUndoHistoryAction(helloDecision.nextState, comma)
        assertEquals(UndoHistoryAction.REPLACE_CURRENT, commaDecision.action)

        val nextWord = describeTextEdit("Привет,м", "Привет, ми", 8, 8)!!
        assertEquals(
            UndoHistoryAction.APPEND,
            decideUndoHistoryAction(commaDecision.nextState, nextWord).action
        )
    }

    @Test
    fun pastedTextIsOneUndoActionAndDoesNotMergeWithFollowingTyping() {
        val paste = describeTextEdit("", "Привет мир", 0, 0)!!
        val pasteDecision = decideUndoHistoryAction(UndoGroupingState(), paste)
        assertEquals(UndoHistoryAction.APPEND, pasteDecision.action)
        assertEquals(UndoGroupingState(), pasteDecision.nextState)

        val following = describeTextEdit("Привет мир", "Привет мир!", 10, 10)!!
        assertEquals(
            UndoHistoryAction.APPEND,
            decideUndoHistoryAction(pasteDecision.nextState, following).action
        )
    }

    @Test
    fun consecutiveBackspacesWithinOneWordFormOneUndoGroup() {
        val firstDelete = describeTextEdit("Привет", "Приве", 6, 6)!!
        val firstDecision = decideUndoHistoryAction(UndoGroupingState(), firstDelete)
        assertEquals(UndoHistoryAction.APPEND, firstDecision.action)

        val secondDelete = describeTextEdit("Приве", "Прив", 5, 5)!!
        assertEquals(
            UndoHistoryAction.REPLACE_CURRENT,
            decideUndoHistoryAction(firstDecision.nextState, secondDelete).action
        )
    }

    @Test
    fun replacingSelectedTextIsOneUndoAction() {
        val replacement = describeTextEdit("Привет мир", "Привет друг", 7, 10)!!
        assertFalse(replacement.simpleCharacterChange)
        assertEquals(
            UndoHistoryAction.APPEND,
            decideUndoHistoryAction(UndoGroupingState(), replacement).action
        )
    }

    @Test
    fun externalTextChangeKeepsCaretInUnchangedPrefix() {
        val mapped = mapTextSelection(
            oldText = "Первая строка\nВторая строка",
            newText = "Первая строка\nИзменённая строка",
            selection = androidx.compose.ui.text.TextRange(6)
        )
        assertEquals(androidx.compose.ui.text.TextRange(6), mapped)
    }

    @Test
    fun externalTextChangeShiftsCaretWithUnchangedSuffix() {
        val mapped = mapTextSelection(
            oldText = "Alpha\n\nBeta",
            newText = "New paragraph\n\nAlpha\n\nBeta",
            selection = androidx.compose.ui.text.TextRange(10)
        )
        assertEquals(androidx.compose.ui.text.TextRange(23), mapped)
    }

}
