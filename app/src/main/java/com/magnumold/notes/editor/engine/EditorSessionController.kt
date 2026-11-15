package com.magnumold.notes.editor.engine

import com.magnumold.notes.editor.model.CalloutBlock
import com.magnumold.notes.editor.model.ChecklistBlock
import com.magnumold.notes.editor.model.ChecklistItem
import com.magnumold.notes.editor.model.DocumentBlock
import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.model.HeadingBlock
import com.magnumold.notes.editor.model.ListBlock
import com.magnumold.notes.editor.model.ListItem
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
    val composingRange: TextRange? = null,
    val activeItemId: String? = null
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

    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    var state: EditorSessionState = EditorSessionState(
        document = initialDocument,
        activeBlockId = initialDocument.blocks.firstOrNull { it.editableRichText() || it.hasEditableItems() }?.id,
        selection = TextSelection(0, 0)
    )
        private set

    fun activateBlock(blockId: String): EditorSessionState {
        val block = state.document.blocks.firstOrNull { it.id == blockId }
            ?: throw IllegalArgumentException("Block not found: $blockId")
        history.breakCoalescing()
        val firstItemId = when (block) {
            is ListBlock -> block.items.firstOrNull()?.id
            is ChecklistBlock -> block.items.firstOrNull()?.id
            else -> null
        }
        val content = block.richTextForItem(firstItemId) ?: block.richTextOrNull()
        val length = content?.text?.length ?: 0
        state = state.copy(
            activeBlockId = blockId,
            activeItemId = firstItemId,
            selection = TextSelection(length, length),
            composingRange = null
        )
        return state
    }

    fun activateListItem(blockId: String, itemId: String): EditorSessionState =
        activateNestedItem(blockId, itemId, isChecklist = false)

    fun activateChecklistItem(blockId: String, itemId: String): EditorSessionState =
        activateNestedItem(blockId, itemId, isChecklist = true)

    private fun activateNestedItem(
        blockId: String,
        itemId: String,
        isChecklist: Boolean
    ): EditorSessionState {
        val block = state.document.blocks.firstOrNull { it.id == blockId }
            ?: throw IllegalArgumentException("Block not found: $blockId")
        val content = when {
            !isChecklist && block is ListBlock -> findListItem(block.items, itemId)?.content
            isChecklist && block is ChecklistBlock -> findChecklistItem(block.items, itemId)?.content
            else -> null
        } ?: throw IllegalArgumentException("Editable item not found: $itemId")

        history.breakCoalescing()
        state = state.copy(
            activeBlockId = blockId,
            activeItemId = itemId,
            selection = TextSelection(content.text.length, content.text.length),
            composingRange = null
        )
        return state
    }

    fun setSelection(anchor: Int, focus: Int): EditorSessionState {
        val length = activeRichText()?.text?.length ?: 0
        val nextSelection = TextSelection(anchor.coerceIn(0, length), focus.coerceIn(0, length))
        if (nextSelection != state.selection) history.breakCoalescing()
        state = state.copy(
            selection = nextSelection,
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
        val content = activeRichText() ?: return state
        val range = state.selection.asRange()
        val updated = RichTextOperations.replaceRange(content, range, replacement)
        if (updated == content) return state

        val updatedBlock = block.withRichTextForTarget(state.activeItemId, updated)
        replaceActiveBlock(updatedBlock, "text:${block.id}:${state.activeItemId.orEmpty()}")
        val cursor = range.start + replacement.length
        state = state.copy(selection = TextSelection(cursor, cursor), composingRange = null)
        return state
    }

    fun applyInlineStyle(
        style: com.magnumold.notes.editor.model.InlineStyle,
        enabled: Boolean
    ): EditorSessionState {
        val block = activeBlock() ?: return state
        val content = activeRichText() ?: return state
        if (state.selection.isCollapsed) return state
        history.breakCoalescing()
        val updated = RichTextOperations.setStyle(content, state.selection.asRange(), style, enabled)
        if (updated != content) {
            replaceActiveBlock(block.withRichTextForTarget(state.activeItemId, updated))
        }
        return state
    }

    fun execute(command: DocumentCommand): EditorSessionState {
        history.breakCoalescing()
        val document = history.execute(command)
        val preferred = when (command) {
            is DocumentCommand.Insert -> command.block.id
            is DocumentCommand.Replace -> command.block.id
            else -> state.activeBlockId
        }
        state = stateForDocument(document, preferred, state.activeItemId, state.selection)
        return state
    }

    fun undo(): EditorSessionState {
        history.breakCoalescing()
        val document = history.undo() ?: return state
        state = stateForDocument(document, state.activeBlockId, state.activeItemId, state.selection)
        return state
    }

    fun redo(): EditorSessionState {
        history.breakCoalescing()
        val document = history.redo() ?: return state
        state = stateForDocument(document, state.activeBlockId, state.activeItemId, state.selection)
        return state
    }

    private fun replaceActiveBlock(updated: DocumentBlock, coalescingKey: String? = null) {
        val document = history.execute(DocumentCommand.Replace(updated), coalescingKey)
        state = stateForDocument(document, updated.id, state.activeItemId, state.selection)
    }

    private fun activeBlock(): DocumentBlock? =
        state.document.blocks.firstOrNull { it.id == state.activeBlockId }

    private fun activeRichText(): RichText? =
        activeBlock()?.richTextForItem(state.activeItemId) ?: activeBlock()?.richTextOrNull()

    private fun stateForDocument(
        document: EditorDocument,
        preferredBlockId: String?,
        preferredItemId: String?,
        previousSelection: TextSelection
    ): EditorSessionState {
        val active = document.blocks.firstOrNull { it.id == preferredBlockId && (it.editableRichText() || it.hasEditableItems()) }
            ?: document.blocks.firstOrNull { it.editableRichText() || it.hasEditableItems() }
        val itemId = when (active) {
            is ListBlock -> preferredItemId?.takeIf { findListItem(active.items, it) != null }
                ?: active.items.firstOrNull()?.id
            is ChecklistBlock -> preferredItemId?.takeIf { findChecklistItem(active.items, it) != null }
                ?: active.items.firstOrNull()?.id
            else -> null
        }
        val length = active?.richTextForItem(itemId)?.text?.length
            ?: active?.richTextOrNull()?.text?.length ?: 0
        return EditorSessionState(
            document = document,
            activeBlockId = active?.id,
            activeItemId = itemId,
            selection = TextSelection(
                previousSelection.anchor.coerceIn(0, length),
                previousSelection.focus.coerceIn(0, length)
            )
        )
    }
}

private fun DocumentBlock.editableRichText(): Boolean = richTextOrNull() != null

private fun DocumentBlock.hasEditableItems(): Boolean =
    (this is ListBlock && items.isNotEmpty()) || (this is ChecklistBlock && items.isNotEmpty())

private fun DocumentBlock.richTextOrNull(): RichText? = when (this) {
    is ParagraphBlock -> content
    is HeadingBlock -> content
    is QuoteBlock -> content
    is CalloutBlock -> content
    else -> null
}

private fun DocumentBlock.richTextForItem(itemId: String?): RichText? = when (this) {
    is ListBlock -> itemId?.let { findListItem(items, it)?.content }
    is ChecklistBlock -> itemId?.let { findChecklistItem(items, it)?.content }
    else -> null
}

private fun DocumentBlock.withRichTextForTarget(itemId: String?, value: RichText): DocumentBlock = when (this) {
    is ParagraphBlock -> copy(content = value)
    is HeadingBlock -> copy(content = value)
    is QuoteBlock -> copy(content = value)
    is CalloutBlock -> copy(content = value)
    is ListBlock -> copy(items = updateListItem(items, requireNotNull(itemId), value))
    is ChecklistBlock -> copy(items = updateChecklistItem(items, requireNotNull(itemId), value))
    else -> this
}

private fun findListItem(items: List<ListItem>, id: String): ListItem? {
    items.forEach { item ->
        if (item.id == id) return item
        findListItem(item.children, id)?.let { return it }
    }
    return null
}

private fun updateListItem(items: List<ListItem>, id: String, content: RichText): List<ListItem> =
    items.map { item ->
        when {
            item.id == id -> item.copy(content = content)
            else -> item.copy(children = updateListItem(item.children, id, content))
        }
    }

private fun findChecklistItem(items: List<ChecklistItem>, id: String): ChecklistItem? {
    items.forEach { item ->
        if (item.id == id) return item
        findChecklistItem(item.children, id)?.let { return it }
    }
    return null
}

private fun updateChecklistItem(
    items: List<ChecklistItem>,
    id: String,
    content: RichText
): List<ChecklistItem> = items.map { item ->
    when {
        item.id == id -> item.copy(content = content)
        else -> item.copy(children = updateChecklistItem(item.children, id, content))
    }
}
