package com.magnumold.notes.editor.engine

import com.magnumold.notes.editor.model.CalloutBlock
import com.magnumold.notes.editor.model.DocumentBlock
import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.model.HeadingBlock
import com.magnumold.notes.editor.model.ParagraphBlock
import com.magnumold.notes.editor.model.QuoteBlock
import com.magnumold.notes.editor.model.RichText

/**
 * UI-independent editing state. Offsets are UTF-16 indices, matching Kotlin String
 * and Android text APIs, so selections can be transferred without conversion.
 */
internal data class EditorSessionState(
    val document: EditorDocument,
    val activeBlockId: String?,
    val selection: TextSelection,
    val composingRange: TextRange? = null
) {
    init {
        require(activeBlockId == null || document.blocks.any { it.id == activeBlockId }) {
            "Active block must belong to the current document"
        }
    }
}

internal class EditorSessionController(
    initialDocument: EditorDocument,
    private val historyLimit: Int = 100
) {
    private val history = DocumentHistory(initialDocument, historyLimit)

    var state: EditorSessionState = EditorSessionState(
        document = initialDocument,
        activeBlockId = initialDocument.blocks.firstOrNull { it.editableRichText() }?.id,
        selection = TextSelection(0, 0)
    )
        private set

    fun activateBlock(blockId: String): EditorSessionState {
        val block = state.document.blocks.firstOrNull { it.id == blockId }
            ?: throw IllegalArgumentException("Block not found: $blockId")
        val length = block.richTextOrNull()?.text?.length ?: 0
        state = state.copy(
            activeBlockId = blockId,
            selection = TextSelection(length, length),
            composingRange = null
        )
        return state
    }

    fun setSelection(anchor: Int, focus: Int): EditorSessionState {
        val length = activeRichText()?.text?.length ?: 0
        state = state.copy(
            selection = TextSelection(anchor.coerceIn(0, length), focus.coerceIn(0, length)),
            composingRange = null
        )
        return state
    }

    fun setComposingRange(range: TextRange?): EditorSessionState {
        val length = activeRichText()?.text?.length ?: 0
        require(range == null || range.end <= length) { "Composing range exceeds active text" }
        state = state.copy(composingRange = range)
        return state
    }

    fun replaceSelection(replacement: String): EditorSessionState {
        val block = activeBlock() ?: return state
        val content = block.richTextOrNull() ?: return state
        val range = state.selection.asRange()
        val updated = RichTextOperations.replaceRange(content, range, replacement)
        if (updated == content) return state

        replaceActiveBlock(block.withRichText(updated))
        val cursor = range.start + replacement.length
        state = state.copy(selection = TextSelection(cursor, cursor), composingRange = null)
        return state
    }

    fun applyInlineStyle(style: com.magnumold.notes.editor.model.InlineStyle, enabled: Boolean): EditorSessionState {
        val block = activeBlock() ?: return state
        val content = block.richTextOrNull() ?: return state
        if (state.selection.isCollapsed) return state
        val updated = RichTextOperations.setStyle(content, state.selection.asRange(), style, enabled)
        if (updated != content) replaceActiveBlock(block.withRichText(updated))
        return state
    }

    fun execute(command: DocumentCommand): EditorSessionState {
        val document = history.execute(command)
        val preferred = when (command) {
            is DocumentCommand.Insert -> command.block.id
            is DocumentCommand.Replace -> command.block.id
            else -> state.activeBlockId
        }
        state = stateForDocument(document, preferred, state.selection)
        return state
    }

    fun undo(): EditorSessionState {
        val document = history.undo() ?: return state
        state = stateForDocument(document, state.activeBlockId, state.selection)
        return state
    }

    fun redo(): EditorSessionState {
        val document = history.redo() ?: return state
        state = stateForDocument(document, state.activeBlockId, state.selection)
        return state
    }

    private fun replaceActiveBlock(updated: DocumentBlock) {
        val document = history.execute(DocumentCommand.Replace(updated))
        state = stateForDocument(document, updated.id, state.selection)
    }

    private fun activeBlock(): DocumentBlock? =
        state.document.blocks.firstOrNull { it.id == state.activeBlockId }

    private fun activeRichText(): RichText? = activeBlock()?.richTextOrNull()

    private fun stateForDocument(
        document: EditorDocument,
        preferredBlockId: String?,
        previousSelection: TextSelection
    ): EditorSessionState {
        val active = document.blocks.firstOrNull { it.id == preferredBlockId && it.editableRichText() }
            ?: document.blocks.firstOrNull { it.editableRichText() }
        val length = active?.richTextOrNull()?.text?.length ?: 0
        return EditorSessionState(
            document = document,
            activeBlockId = active?.id,
            selection = TextSelection(
                previousSelection.anchor.coerceIn(0, length),
                previousSelection.focus.coerceIn(0, length)
            )
        )
    }
}

private fun DocumentBlock.editableRichText(): Boolean = richTextOrNull() != null

private fun DocumentBlock.richTextOrNull(): RichText? = when (this) {
    is ParagraphBlock -> content
    is HeadingBlock -> content
    is QuoteBlock -> content
    is CalloutBlock -> content
    else -> null
}

private fun DocumentBlock.withRichText(value: RichText): DocumentBlock = when (this) {
    is ParagraphBlock -> copy(content = value)
    is HeadingBlock -> copy(content = value)
    is QuoteBlock -> copy(content = value)
    is CalloutBlock -> copy(content = value)
    else -> this
}
