package com.magnumold.notes

internal enum class RecordType {
    NOTE,
    TASK,
    PURCHASE
}

internal enum class RecordStatus {
    WAITING,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    CANCELLED,
    MISSED
}

internal enum class RecordDateMode {
    DATE,
    DEADLINE
}

internal object RecordStatusRules {
    fun applyDeadline(
        status: RecordStatus,
        dateMode: RecordDateMode?,
        dateAt: Long?,
        now: Long
    ): RecordStatus {
        if (status == RecordStatus.COMPLETED || status == RecordStatus.PAUSED ||
            status == RecordStatus.CANCELLED || status == RecordStatus.MISSED
        ) return status
        if (dateMode != RecordDateMode.DEADLINE || dateAt == null) return status
        return if (now >= dateAt + 86_400_000L) RecordStatus.MISSED else status
    }
}

internal data class NoteBlock(val text: String = "", val checked: Boolean = false, val isChecklist: Boolean = false, val id: String = java.util.UUID.randomUUID().toString())
internal data class Note(
    val id: Long, val title: String, val blocks: List<NoteBlock>, val updated: Long,
    val colorIndex: Int = 0, val pinned: Boolean = false, val created: Long = updated,
    val type: RecordType = RecordType.NOTE, val status: RecordStatus = RecordStatus.WAITING,
    val dateMode: RecordDateMode? = null, val dateAt: Long? = null,
    val titleEnabled: Boolean = false, val titleText: String = "",
    val descriptionEnabled: Boolean = false, val descriptionText: String = "",
    val deadlineResumeOverride: Boolean = false, val children: List<Note> = emptyList(),
    val statusBeforeAutoCompletion: RecordStatus? = null,
    val timeTrackingEnabled: Boolean = false
)

internal data class NoteCollection(val id: Long, val name: String, val notes: List<Note> = emptyList())
internal data class PersistenceSnapshot(
    val notes: List<Note>,
    val collections: List<NoteCollection>,
    val archived: List<VaultNote>,
    val trash: List<VaultNote>
)
internal data class VaultNote(val note: Note, val collectionId: Long?, val position: Int, val movedAt: Long)

internal fun List<NoteBlock>.toPlainText(): String = joinToString("\n\n") { block ->
    (if (block.isChecklist) (if (block.checked) "☑ " else "☐ ") else "") + block.text
}

/**
 * Rebuilds editor blocks from the plain-text representation while retaining
 * metadata and stable IDs for blocks that still occupy the same paragraph.
 * Checklist markers are part of the editor text, so toggling/removing one is
 * reflected in the model rather than silently discarded.
 */
internal fun List<NoteBlock>.withEditedPlainText(value: String): List<NoteBlock> {
    val paragraphs = value.split("\n\n")
    val oldParagraphs = map { block ->
        (if (block.isChecklist) (if (block.checked) "☑ " else "☐ ") else "") + block.text
    }

    // Match the unchanged prefix and suffix. This is linear-time and avoids
    // an expensive alignment matrix on every keystroke in long notes.
    val oldCount = oldParagraphs.size
    val newCount = paragraphs.size
    val matchedOldIndex = IntArray(newCount) { -1 }
    val sharedLimit = minOf(oldCount, newCount)

    var prefix = 0
    while (prefix < sharedLimit && oldParagraphs[prefix] == paragraphs[prefix]) {
        matchedOldIndex[prefix] = prefix
        prefix++
    }

    var suffix = 0
    while (
        suffix < oldCount - prefix &&
        suffix < newCount - prefix &&
        oldParagraphs[oldCount - suffix - 1] == paragraphs[newCount - suffix - 1]
    ) {
        val oldIndex = oldCount - suffix - 1
        val newIndex = newCount - suffix - 1
        matchedOldIndex[newIndex] = oldIndex
        suffix++
    }

    // A one-for-one middle segment represents edits to existing paragraphs.
    // If its size changes, leave it unmatched rather than transferring IDs
    // or checklist metadata to a potentially different paragraph.
    val oldMiddleCount = oldCount - prefix - suffix
    val newMiddleCount = newCount - prefix - suffix
    if (oldMiddleCount == newMiddleCount) {
        for (offset in 0 until oldMiddleCount) {
            matchedOldIndex[prefix + offset] = prefix + offset
        }
    }

    return paragraphs.mapIndexed { index, paragraph ->
        val previous = getOrNull(matchedOldIndex[index])
        val checked = paragraph.startsWith("☑ ")
        val hasChecklistMarker = checked || paragraph.startsWith("☐ ")
        val text = if (hasChecklistMarker) paragraph.substring(2) else paragraph
        if (previous != null) {
            previous.copy(
                text = text,
                checked = checked,
                isChecklist = hasChecklistMarker
            )
        } else {
            NoteBlock(
                text = text,
                checked = checked,
                isChecklist = hasChecklistMarker
            )
        }
    }
}

internal fun Note.matchesSearch(query: String): Boolean =
    blocks.any { it.text.contains(query, ignoreCase = true) } ||
        titleText.contains(query, ignoreCase = true) ||
        descriptionText.contains(query, ignoreCase = true) ||
        children.any { it.matchesSearch(query) }

internal fun Note.hasContent(): Boolean =
    blocks.any { it.text.isNotBlank() } ||
        titleText.isNotBlank() ||
        descriptionText.isNotBlank() ||
        children.any { it.hasContent() }

internal fun Note.recordStatus(now: Long = System.currentTimeMillis()): RecordStatus =
    if (deadlineResumeOverride) status
    else RecordStatusRules.applyDeadline(status, dateMode, dateAt, now)

internal fun withUpdatedRecordChildren(parent: Note, children: List<Note>): Note {
    val wasAllCompleted = parent.children.isNotEmpty() &&
        parent.children.all { it.recordStatus() == RecordStatus.COMPLETED }
    val isAllCompleted = children.isNotEmpty() &&
        children.all { it.recordStatus() == RecordStatus.COMPLETED }
    return when {
        isAllCompleted && !wasAllCompleted -> parent.copy(
            children = children,
            status = RecordStatus.COMPLETED,
            statusBeforeAutoCompletion = parent.status
        )
        wasAllCompleted && !isAllCompleted && parent.statusBeforeAutoCompletion != null ->
            parent.copy(
                children = children,
                status = parent.statusBeforeAutoCompletion,
                statusBeforeAutoCompletion = null
            )
        else -> parent.copy(children = children)
    }
}

internal fun newRecordId(): Long {
    var id: Long
    do {
        id = java.util.UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE
    } while (id == 0L)
    return id
}

