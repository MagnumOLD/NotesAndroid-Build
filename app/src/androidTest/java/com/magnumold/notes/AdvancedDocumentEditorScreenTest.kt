package com.magnumold.notes

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.magnumold.notes.editor.model.AttachmentBlock
import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.model.HeadingBlock
import com.magnumold.notes.editor.model.ParagraphBlock
import com.magnumold.notes.editor.model.RichText
import com.magnumold.notes.editor.model.plainText
import com.magnumold.notes.editor.ui.AdvancedDocumentEditorScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AdvancedDocumentEditorScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersDocumentAndAddsHeadingBlock() {
        val initial = EditorDocument(
            id = "ui-test-document",
            createdAt = 1L,
            updatedAt = 1L,
            blocks = listOf(ParagraphBlock(content = RichText("Черновик документа")))
        )
        var emitted: EditorDocument? = null

        composeRule.setContent {
            MaterialTheme {
                AdvancedDocumentEditorScreen(
                    document = initial,
                    onDocumentChange = { emitted = it },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("advanced-document-editor").assertIsDisplayed()
        composeRule.onNodeWithText("Черновик документа").assertIsDisplayed()
        composeRule.onNodeWithText("Изображение").assertExists()
        composeRule.onNodeWithText("Файл").assertExists()
        composeRule.onNodeWithText("Таблица").assertExists()
        composeRule.onNodeWithText("Ссылка").assertExists()
        composeRule.onNodeWithText("Заголовок").performClick()

        composeRule.runOnIdle {
            assertEquals(2, emitted?.blocks?.size)
            assert(emitted?.blocks?.last() is HeadingBlock)
            assertEquals(1, (emitted?.blocks?.last() as HeadingBlock).level)
        }
    }

    @Test
    fun editsParagraphAndUndoRedoRestoresExactText() {
        val initial = EditorDocument(
            id = "ui-edit-history-document",
            createdAt = 1L,
            updatedAt = 1L,
            blocks = listOf(ParagraphBlock(content = RichText("Начальный текст")))
        )
        var emitted: EditorDocument? = null

        composeRule.setContent {
            MaterialTheme {
                AdvancedDocumentEditorScreen(
                    document = initial,
                    onDocumentChange = { emitted = it },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Начальный текст").performTextInput(" продолжение")
        composeRule.runOnIdle {
            assertEquals("Начальный текст продолжение", emitted?.plainText())
        }

        composeRule.onNodeWithContentDescription("Отменить").assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals("Начальный текст", emitted?.plainText())
        }

        composeRule.onNodeWithContentDescription("Повторить").performClick()
        composeRule.runOnIdle {
            assertEquals("Начальный текст продолжение", emitted?.plainText())
        }
    }

    @Test
    fun keepsAllCharactersAcrossSequentialInputEvents() {
        val initial = EditorDocument(
            id = "ui-sequential-input-document",
            createdAt = 1L,
            updatedAt = 1L,
            blocks = listOf(ParagraphBlock(content = RichText("")))
        )
        var emitted: EditorDocument? = null

        composeRule.setContent {
            MaterialTheme {
                AdvancedDocumentEditorScreen(
                    document = initial,
                    onDocumentChange = { emitted = it },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Начните писать…").performTextInput("Привет")
        composeRule.onNodeWithText("Привет").performTextInput(" мир")
        composeRule.onNodeWithText("Привет мир").performTextInput("!")
        composeRule.runOnIdle {
            assertEquals("Привет мир!", emitted?.plainText())
        }
    }

    @Test
    fun rendersImportedAttachmentCard() {
        val initial = EditorDocument(
            id = "ui-attachment-document",
            createdAt = 1L,
            updatedAt = 1L,
            blocks = listOf(
                AttachmentBlock(
                    assetId = "00000000-0000-0000-0000-000000000001",
                    fileName = "reference.pdf",
                    mimeType = "application/pdf",
                    sizeBytes = 2048L
                )
            )
        )

        composeRule.setContent {
            MaterialTheme {
                AdvancedDocumentEditorScreen(
                    document = initial,
                    onDocumentChange = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("reference.pdf").assertIsDisplayed()
        composeRule.onNodeWithText("application/pdf · 2 КБ").assertIsDisplayed()
    }

}
