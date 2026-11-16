package com.magnumold.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class NoteEditorCursorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingBlankAreaBelowNoteTextKeepsTypingAvailable() {
        var note by mutableStateOf(
            Note(
                id = 7002L,
                title = "",
                blocks = listOf(NoteBlock("Existing note text", id = "body")),
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
        // Tap the center of the expanded editor, below the one-line note content.
        editor.performTouchInput {
            click(center)
        }
        editor.performTextInput(" continued")

        editor.assertTextEquals("Existing note text continued")
    }

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
        editor.assertTextEquals("abcdefX")
    }

    @Test
    fun insertionAtMiddleCaretAndBackspaceKeepEditorAndModelInSync() {
        var note by mutableStateOf(
            Note(
                id = 7003L,
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
        editor.performTextInputSelection(TextRange(3))
        editor.performTextInput("X")
        editor.assertTextEquals("abcXdef")
        assertEquals("abcXdef", note.blocks.toPlainText())
        assertEquals("body", note.blocks.single().id)

        editor.performKeyInput { pressKey(Key.Backspace) }
        editor.assertTextEquals("abcdef")
        assertEquals("abcdef", note.blocks.toPlainText())
        assertEquals("body", note.blocks.single().id)
    }

    @Test
    fun replacingSelectedTextUpdatesTheNoteWithoutLosingItsBlockId() {
        var note by mutableStateOf(
            Note(
                id = 7004L,
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
        editor.performTextInputSelection(TextRange(1, 4))
        editor.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.TextSelectionRange,
                TextRange(1, 4)
            )
        )
        editor.performTextInput("Z")

        editor.assertTextEquals("aZef")
        assertEquals("aZef", note.blocks.toPlainText())
        assertEquals("body", note.blocks.single().id)
    }

    @Test
    fun multilineClipboardPastePreservesParagraphsAndChecklistWhenSaved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        composeRule.runOnUiThread {
            clipboard.setPrimaryClip(ClipData.newPlainText("pasted note text", " pasted\n\nNew paragraph"))
        }

        var note by mutableStateOf(
            Note(
                id = 7005L,
                title = "",
                blocks = listOf(
                    NoteBlock("Alpha", id = "alpha"),
                    NoteBlock("Buy milk", isChecklist = true, id = "check")
                ),
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
        editor.performTextInputSelection(TextRange("Alpha".length))
        editor.performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.V)
            keyUp(Key.CtrlLeft)
        }
        composeRule.waitForIdle()

        editor.assertTextEquals("Alpha pasted\n\nNew paragraph\n\n☐ Buy milk")
        assertEquals("alpha", note.blocks.first().id)
        assertEquals("check", note.blocks.last().id)
        assertEquals("New paragraph", note.blocks[1].text)
        assertEquals(true, note.blocks.last().isChecklist)

        val preferences = context.getSharedPreferences("note_editor_save_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            NotesRepository(preferences).saveSnapshot(
                PersistenceSnapshot(
                    notes = listOf(note),
                    collections = emptyList(),
                    archived = emptyList(),
                    trash = emptyList()
                )
            )
            val restored = NotesRepository(preferences).loadSnapshot().notes.single()
            assertEquals(note.blocks, restored.blocks)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun selectionRangeSurvivesLocalParentEcho() {
        var note by mutableStateOf(
            Note(
                id = 7006L,
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
        editor.performTextInputSelection(TextRange(1, 4))
        composeRule.waitForIdle()
        SystemClock.sleep(700)
        composeRule.waitForIdle()

        editor.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.TextSelectionRange,
                TextRange(1, 4)
            )
        )
        editor.assertTextEquals("abcdefX")
    }

}
