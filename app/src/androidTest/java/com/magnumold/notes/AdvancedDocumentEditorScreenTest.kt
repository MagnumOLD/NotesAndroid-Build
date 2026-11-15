package com.magnumold.notes

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.model.HeadingBlock
import com.magnumold.notes.editor.model.ParagraphBlock
import com.magnumold.notes.editor.model.RichText
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
        composeRule.onNodeWithText("Заголовок").performClick()

        composeRule.runOnIdle {
            assertEquals(2, emitted?.blocks?.size)
            assert(emitted?.blocks?.last() is HeadingBlock)
            assertEquals(1, (emitted?.blocks?.last() as HeadingBlock).level)
        }
    }
}
