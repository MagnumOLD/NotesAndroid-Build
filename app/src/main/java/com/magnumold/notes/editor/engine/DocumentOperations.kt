package com.magnumold.notes.editor.engine

import com.magnumold.notes.editor.model.DocumentBlock
import com.magnumold.notes.editor.model.EditorDocument

internal sealed interface DocumentCommand {
    data class Insert(val index: Int, val block: DocumentBlock) : DocumentCommand
    data class Delete(val blockId: String) : DocumentCommand
    data class Replace(val block: DocumentBlock) : DocumentCommand
    data class Move(val blockId: String, val targetIndex: Int) : DocumentCommand
}

internal data class CommandResult(
    val document: EditorDocument,
    val inverse: DocumentCommand
)

internal object DocumentOperations {
    fun apply(document: EditorDocument, command: DocumentCommand): CommandResult {
        val blocks = document.blocks.toMutableList()
        val inverse = when (command) {
            is DocumentCommand.Insert -> {
                require(command.index in 0..blocks.size)
                require(blocks.none { it.id == command.block.id }) { "Block id already exists" }
                blocks.add(command.index, command.block)
                DocumentCommand.Delete(command.block.id)
            }
            is DocumentCommand.Delete -> {
                val index = blocks.indexOfFirst { it.id == command.blockId }
                require(index >= 0) { "Block not found: ${command.blockId}" }
                val removed = blocks.removeAt(index)
                DocumentCommand.Insert(index, removed)
            }
            is DocumentCommand.Replace -> {
                val index = blocks.indexOfFirst { it.id == command.block.id }
                require(index >= 0) { "Block not found: ${command.block.id}" }
                val previous = blocks.set(index, command.block)
                require(previous.id == command.block.id)
                DocumentCommand.Replace(previous)
            }
            is DocumentCommand.Move -> {
                val from = blocks.indexOfFirst { it.id == command.blockId }
                require(from >= 0) { "Block not found: ${command.blockId}" }
                require(command.targetIndex in 0 until blocks.size)
                val moved = blocks.removeAt(from)
                blocks.add(command.targetIndex, moved)
                DocumentCommand.Move(command.blockId, from)
            }
        }
        return CommandResult(document.copy(updatedAt = System.currentTimeMillis(), blocks = blocks), inverse)
    }
}

private data class HistoryEntry(
    val snapshot: EditorDocument,
    val inverse: DocumentCommand
)

/**
 * Structural commands are individual history entries. Consecutive text replacements
 * for the same editing target share one entry, so one Undo reverses a typing run.
 */
internal class DocumentHistory(
    initial: EditorDocument,
    private val maxEntries: Int = 100
) {
    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<Pair<EditorDocument, DocumentCommand>>()
    private var openCoalescingKey: String? = null

    var current: EditorDocument = initial
        private set

    init { require(maxEntries > 0) }

    fun breakCoalescing() {
        openCoalescingKey = null
    }

    fun execute(command: DocumentCommand, coalescingKey: String? = null): EditorDocument {
        val result = DocumentOperations.apply(current, command)
        val canCoalesce = coalescingKey != null && coalescingKey == openCoalescingKey
        if (!canCoalesce) {
            undoStack.addLast(HistoryEntry(current, result.inverse))
            while (undoStack.size > maxEntries) undoStack.removeFirst()
        }
        openCoalescingKey = coalescingKey
        current = result.document
        redoStack.clear()
        return current
    }

    fun undo(): EditorDocument? {
        breakCoalescing()
        val entry = undoStack.removeLastOrNull() ?: return null
        val result = DocumentOperations.apply(current, entry.inverse)
        redoStack.addLast(entry.snapshot to commandForTransition(result.document, current))
        current = result.document
        return current
    }

    fun redo(): EditorDocument? {
        breakCoalescing()
        val entry = redoStack.removeLastOrNull() ?: return null
        val result = DocumentOperations.apply(current, entry.second)
        undoStack.addLast(HistoryEntry(entry.first, commandForTransition(result.document, current)))
        current = result.document
        return current
    }

    private fun commandForTransition(from: EditorDocument, to: EditorDocument): DocumentCommand {
        val fromIds = from.blocks.map { it.id }
        val toIds = to.blocks.map { it.id }
        if (fromIds.size < toIds.size) {
            val added = to.blocks.first { candidate -> from.blocks.none { it.id == candidate.id } }
            return DocumentCommand.Insert(to.blocks.indexOfFirst { it.id == added.id }, added)
        }
        if (fromIds.size > toIds.size) {
            val removed = from.blocks.first { candidate -> to.blocks.none { it.id == candidate.id } }
            return DocumentCommand.Delete(removed.id)
        }
        val changed = to.blocks.firstOrNull { target ->
            from.blocks.firstOrNull { it.id == target.id } != target
        }
        if (changed != null) return DocumentCommand.Replace(changed)
        val movedId = toIds.firstOrNull { id -> fromIds.indexOf(id) != toIds.indexOf(id) }
            ?: error("Documents do not represent a single reversible operation")
        return DocumentCommand.Move(movedId, toIds.indexOf(movedId))
    }
}
