package com.magnumold.notes

import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteEditorCursorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun caretStaysAtRequestedPositionAfterLocalTextEchoFromParent() {
        var note by mutableStateOf(
            Note(
                id = 7001L,
                title = "",
                blocks = listOf(NoteBlock("abcdef", id = "body")),
                updated = 1L
            )
        )

        composeRule.setContent {
            MaterialTheme {
                NoteEditor(
                    note = note,
                    dark = false,
                    topBarColor = Color(0xFF202020),
                    addButtonColor = Color(0xFF607D8B),
                    selectionColor = Color(0xFF607D8B),
                    autoFocus = false,
                    onBack = {},
                    onChange = { note = it },
                    onDelete = {},
                    onShare = {}
                )
            }
        }

        val editor = composeRule.onNodeWithTag("note-editor-body")
        editor.performClick()
        editor.performTextInput("X")
        editor.assertTextEquals("abcdefX")

        // Moving the caret after the parent has received the local text update
        // must not be undone by a delayed Compose state echo.
        editor.performTextInputSelection(TextRange(3))
        composeRule.waitForIdle()
        SystemClock.sleep(700)
        composeRule.waitForIdle()

        editor.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.TextSelectionRange,
                TextRange(3)
            )
        )
        editor.assertTextEquals("abcXdef")
    }
}
