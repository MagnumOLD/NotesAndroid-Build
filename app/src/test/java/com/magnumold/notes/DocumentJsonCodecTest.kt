package com.magnumold.notes

import com.magnumold.notes.editor.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DocumentJsonCodecTest {
    @Test fun roundTripsAllInitialBlockTypes() {
        val document = EditorDocument(
            id = "doc-1", createdAt = 10L, updatedAt = 20L,
            metadata = DocumentMetadata("Travel", listOf("personal", "trip")),
            blocks = listOf(
                ParagraphBlock(content = RichText("Hello", listOf(InlineSpan(0, 5, setOf(InlineStyle.BOLD))))),
                HeadingBlock(level = 2, content = RichText("Plan")),
                ListBlock(ordered = false, items = listOf(ListItem(content = RichText("Item")))),
                ChecklistBlock(items = listOf(ChecklistItem(content = RichText("Done"), checked = true))),
                QuoteBlock(content = RichText("Quote")),
                CodeBlock(code = "val x = 1", language = "kotlin"),
                TableBlock(rows = listOf(TableRow(cells = listOf(TableCell(RichText("A")), TableCell(RichText("B")))))),
                ImageBlock(assetId = "asset-1", caption = "Photo"),
                AttachmentBlock(assetId = "asset-2", fileName = "file.pdf"),
                LinkBlock(label = "Site", url = "https://example.com"),
                CalloutBlock(kind = CalloutKind.WARNING, title = "Careful", content = RichText("Warning")),
                DividerBlock()
            )
        )
        val decoded = DocumentJsonCodec.decode(DocumentJsonCodec.encode(document))
        assertEquals(document, decoded)
    }

    @Test fun plainTextKeepsTextualContentReadable() {
        val document = EditorDocument(
            id = "doc-2", createdAt = 1, updatedAt = 1,
            blocks = listOf(ParagraphBlock(content = RichText("First")), HeadingBlock(level = 1, content = RichText("Second")))
        )
        assertTrue(document.plainText().contains("First"))
        assertTrue(document.plainText().contains("Second"))
    }
}
