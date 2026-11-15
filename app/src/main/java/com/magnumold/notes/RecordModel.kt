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

    // Find unchanged paragraphs in order using a longest-common-subsequence
    // alignment. These anchors keep IDs stable when paragraphs are inserted
    // or removed before them.
    val oldCount = oldParagraphs.size
    val newCount = paragraphs.size
    val lcs = Array(oldCount + 1) { IntArray(newCount + 1) }
    for (oldIndex in oldCount - 1 downTo 0) {
        for (newIndex in newCount - 1 downTo 0) {
            lcs[oldIndex][newIndex] =
                if (oldParagraphs[oldIndex] == paragraphs[newIndex]) {
                    1 + lcs[oldIndex + 1][newIndex + 1]
                } else {
                    maxOf(lcs[oldIndex + 1][newIndex], lcs[oldIndex][newIndex + 1])
                }
        }
    }

    val matchedOldIndex = IntArray(newCount) { -1 }
    val usedOldIndices = BooleanArray(oldCount)
    val anchors = mutableListOf<Pair<Int, Int>>()
    var oldIndex = 0
    var newIndex = 0
    while (oldIndex < oldCount && newIndex < newCount) {
        when {
            oldParagraphs[oldIndex] == paragraphs[newIndex] -> {
                anchors += oldIndex to newIndex
                oldIndex++
                newIndex++
            }
            lcs[oldIndex + 1][newIndex] >= lcs[oldIndex][newIndex + 1] -> oldIndex++
            else -> newIndex++
        }
    }

    anchors.forEach { (oldMatch, newMatch) ->
        matchedOldIndex[newMatch] = oldMatch
        usedOldIndices[oldMatch] = true
    }

    // Between unchanged anchors, a one-for-one replacement is treated as an
    // edit of the existing block. Unequal gaps are left unmatched rather than
    // assigning metadata to the wrong paragraph.
    var previousOld = -1
    var previousNew = -1
    (anchors + listOf(oldCount to newCount)).forEach { (nextOld, nextNew) ->
        val oldGap = nextOld - previousOld - 1
        val newGap = nextNew - previousNew - 1
        if (oldGap == newGap) {
            for (offset in 1..oldGap) {
                val oldMatch = previousOld + offset
                val newMatch = previousNew + offset
                matchedOldIndex[newMatch] = oldMatch
                usedOldIndices[oldMatch] = true
            }
        }
        previousOld = nextOld
        previousNew = nextNew
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

