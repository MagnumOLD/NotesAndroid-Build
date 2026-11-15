package com.magnumold.notes.editor.model

import java.util.UUID

internal const val CURRENT_DOCUMENT_SCHEMA_VERSION = 1

internal fun newBlockId(): String = UUID.randomUUID().toString()

internal data class DocumentMetadata(
    val title: String = "",
    val tags: List<String> = emptyList()
)

internal data class EditorDocument(
    val id: String,
    val schemaVersion: Int = CURRENT_DOCUMENT_SCHEMA_VERSION,
    val createdAt: Long,
    val updatedAt: Long,
    val metadata: DocumentMetadata = DocumentMetadata(),
    val blocks: List<DocumentBlock>
) {
    init {
        require(id.isNotBlank()) { "Document id must not be blank" }
        require(schemaVersion > 0) { "Document schema version must be positive" }
        require(blocks.map { it.id }.toSet().size == blocks.size) { "Block ids must be unique within a document" }
    }
}

internal enum class InlineStyle { BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, CODE }

internal data class InlineSpan(
    val start: Int,
    val end: Int,
    val styles: Set<InlineStyle> = emptySet(),
    val foregroundArgb: Long? = null,
    val backgroundArgb: Long? = null,
    val link: String? = null
) {
    init {
        require(start >= 0 && end > start) { "Inline span must have a non-empty, non-negative range" }
    }
}

internal data class RichText(
    val text: String,
    val spans: List<InlineSpan> = emptyList()
) {
    init {
        require(spans.all { it.end <= text.length }) { "Inline span exceeds text length" }
    }
}

internal sealed interface DocumentBlock {
    val id: String
}

internal data class ParagraphBlock(
    override val id: String = newBlockId(),
    val content: RichText = RichText("")
) : DocumentBlock

internal data class HeadingBlock(
    override val id: String = newBlockId(),
    val level: Int,
    val content: RichText
) : DocumentBlock {
    init { require(level in 1..3) { "Heading level must be between 1 and 3" } }
}

internal data class ListItem(
    val id: String = newBlockId(),
    val content: RichText,
    val children: List<ListItem> = emptyList()
)

internal data class ListBlock(
    override val id: String = newBlockId(),
    val ordered: Boolean,
    val items: List<ListItem>
) : DocumentBlock

internal data class ChecklistItem(
    val id: String = newBlockId(),
    val content: RichText,
    val checked: Boolean = false,
    val children: List<ChecklistItem> = emptyList()
)

internal data class ChecklistBlock(
    override val id: String = newBlockId(),
    val items: List<ChecklistItem>
) : DocumentBlock

internal data class QuoteBlock(
    override val id: String = newBlockId(),
    val content: RichText
) : DocumentBlock

internal data class CodeBlock(
    override val id: String = newBlockId(),
    val code: String,
    val language: String? = null
) : DocumentBlock

internal data class TableCell(val content: RichText = RichText(""))
internal data class TableRow(val id: String = newBlockId(), val cells: List<TableCell>)

internal data class TableBlock(
    override val id: String = newBlockId(),
    val rows: List<TableRow>
) : DocumentBlock {
    init {
        require(rows.isNotEmpty()) { "A table must contain at least one row" }
        val width = rows.first().cells.size
        require(width > 0 && rows.all { it.cells.size == width }) {
            "All table rows must have the same non-zero number of cells"
        }
    }
}

internal enum class ImageAlignment { START, CENTER, END }

internal data class ImageBlock(
    override val id: String = newBlockId(),
    val assetId: String,
    val widthFraction: Float = 1f,
    val alignment: ImageAlignment = ImageAlignment.CENTER,
    val caption: String = ""
) : DocumentBlock {
    init {
        require(assetId.isNotBlank()) { "Image asset id must not be blank" }
        require(widthFraction in 0.1f..1f) { "Image width fraction must be between 0.1 and 1" }
    }
}

internal data class AttachmentBlock(
    override val id: String = newBlockId(),
    val assetId: String,
    val fileName: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null
) : DocumentBlock {
    init {
        require(assetId.isNotBlank() && fileName.isNotBlank())
        require(sizeBytes == null || sizeBytes >= 0)
    }
}

internal data class LinkBlock(
    override val id: String = newBlockId(),
    val label: String,
    val url: String
) : DocumentBlock {
    init { require(url.isNotBlank()) }
}

internal enum class CalloutKind { NOTE, TIP, WARNING, IMPORTANT }

internal data class CalloutBlock(
    override val id: String = newBlockId(),
    val kind: CalloutKind,
    val title: String = "",
    val content: RichText
) : DocumentBlock

internal data class DividerBlock(
    override val id: String = newBlockId()
) : DocumentBlock

internal fun EditorDocument.plainText(): String = blocks.mapNotNull { block ->
    when (block) {
        is ParagraphBlock -> block.content.text
        is HeadingBlock -> block.content.text
        is ListBlock -> block.items.joinToString("\n") { it.content.text }
        is ChecklistBlock -> block.items.joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.content.text }
        is QuoteBlock -> block.content.text
        is CodeBlock -> block.code
        is TableBlock -> block.rows.joinToString("\n") { row -> row.cells.joinToString("\t") { it.content.text } }
        is ImageBlock -> block.caption.takeIf(String::isNotBlank)
        is AttachmentBlock -> block.fileName
        is LinkBlock -> block.label.ifBlank { block.url }
        is CalloutBlock -> block.content.text
        is DividerBlock -> null
    }
}.joinToString("\n\n")
