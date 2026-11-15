package com.magnumold.notes.editor.engine

import com.magnumold.notes.editor.model.InlineSpan
import com.magnumold.notes.editor.model.InlineStyle
import com.magnumold.notes.editor.model.RichText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextOperationsTest {
    @Test fun replaceRangeShiftsSpansAfterInsertedText() {
        val original = RichText(
            "hello world",
            listOf(InlineSpan(6, 11, setOf(InlineStyle.BOLD)))
        )

        val edited = RichTextOperations.replaceRange(original, TextRange(0, 0), "Say ")

        assertEquals("Say hello world", edited.text)
        assertEquals(listOf(InlineSpan(10, 15, setOf(InlineStyle.BOLD))), edited.spans)
    }

    @Test fun insertionInsideFormattedRunKeepsFormattingAcrossInsertedText() {
        val original = RichText("abcd", listOf(InlineSpan(0, 4, setOf(InlineStyle.ITALIC))))

        val edited = RichTextOperations.replaceRange(original, TextRange(2, 2), "XY")

        assertEquals("abXYcd", edited.text)
        assertEquals(listOf(InlineSpan(0, 6, setOf(InlineStyle.ITALIC))), edited.spans)
    }

    @Test fun replacingMiddleOfFormattedRunPreservesFormattingOutsideSelection() {
        val original = RichText("abcdef", listOf(InlineSpan(0, 6, setOf(InlineStyle.BOLD))))

        val edited = RichTextOperations.replaceRange(original, TextRange(2, 4), "XYZ")

        assertEquals("abXYZef", edited.text)
        assertEquals(
            listOf(
                InlineSpan(0, 2, setOf(InlineStyle.BOLD)),
                InlineSpan(5, 7, setOf(InlineStyle.BOLD))
            ),
            edited.spans
        )
    }

    @Test fun setStyleOnlyChangesRequestedRangeAndMergesAdjacentRuns() {
        val original = RichText("abcdef")

        val bold = RichTextOperations.setStyle(original, TextRange(1, 4), InlineStyle.BOLD, true)
        assertEquals(listOf(InlineSpan(1, 4, setOf(InlineStyle.BOLD))), bold.spans)

        val fullyBold = RichTextOperations.setStyle(bold, TextRange(4, 6), InlineStyle.BOLD, true)
        assertEquals(listOf(InlineSpan(1, 6, setOf(InlineStyle.BOLD))), fullyBold.spans)

        val unbold = RichTextOperations.setStyle(fullyBold, TextRange(2, 5), InlineStyle.BOLD, false)
        assertEquals(
            listOf(
                InlineSpan(1, 2, setOf(InlineStyle.BOLD)),
                InlineSpan(5, 6, setOf(InlineStyle.BOLD))
            ),
            unbold.spans
        )
    }

    @Test fun selectionNormalizesReverseDirection() {
        val selection = TextSelection(anchor = 8, focus = 3)

        assertEquals(TextRange(3, 8), selection.asRange())
        assertTrue(!selection.isCollapsed)
    }
}
