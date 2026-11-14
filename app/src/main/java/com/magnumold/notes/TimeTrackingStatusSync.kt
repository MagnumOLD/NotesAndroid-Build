package com.magnumold.notes

private data class TaskTreeEntry(
    val note: Note,
    val parentId: Long?,
    val parentTitle: String?
)

/**
 * Applies the approved status-to-stopwatch transitions to a changed note tree.
 * A task with children is a parent and never owns a stopwatch.
 */
internal fun syncWorkSessionsForTaskChanges(
    oldRoots: List<Note>,
    newRoots: List<Note>,
    currentState: TimeTrackingState,
    nowMillis: Long
): TimeTrackingState {
    fun title(note: Note): String =
        note.titleText.takeIf { note.titleEnabled && it.isNotBlank() }
            ?: note.blocks.toPlainText().lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "Задача" }

    fun flatten(roots: List<Note>): Map<Long, TaskTreeEntry> {
        val result = linkedMapOf<Long, TaskTreeEntry>()
        fun visit(note: Note, parentId: Long?, parentTitle: String?) {
            result[note.id] = TaskTreeEntry(note, parentId, parentTitle)
            note.children.forEach { child -> visit(child, note.id, title(note)) }
        }
        roots.forEach { visit(it, null, null) }
        return result
    }

    val oldEntries = flatten(oldRoots)
    val newEntries = flatten(newRoots)
    var state = currentState

    // Deleting a task closes any live session but keeps its title snapshot in history.
    val removedIds = oldEntries.keys - newEntries.keys
    removedIds.forEach { removedId ->
        state = TimeTrackingEngine.finish(state, removedId, nowMillis, WorkSessionEndReason.STOP)
    }
    if (state.protectionTimer?.taskId in removedIds) {
        state = state.copy(protectionTimer = null)
    }

    newEntries.forEach { (id, entry) ->
        val note = entry.note
        if (note.type != RecordType.TASK) return@forEach
        val old = oldEntries[id]?.note
        val wasTrackable = old?.type == RecordType.TASK && oldEntries[id]?.parentId != null && old.timeTrackingEnabled
        val isTrackable = entry.parentId != null && note.timeTrackingEnabled
        val oldStatus = old?.recordStatus(nowMillis)
        val newStatus = note.recordStatus(nowMillis)

        if (wasTrackable && (!isTrackable || (oldStatus == RecordStatus.IN_PROGRESS && newStatus != RecordStatus.IN_PROGRESS))) {
            val reason = when (newStatus) {
                RecordStatus.COMPLETED -> WorkSessionEndReason.TASK_COMPLETED
                RecordStatus.PAUSED -> WorkSessionEndReason.PAUSE
                else -> WorkSessionEndReason.STOP
            }
            state = TimeTrackingEngine.finish(state, id, nowMillis, reason)
        }

        val shouldStart = isTrackable && newStatus == RecordStatus.IN_PROGRESS &&
            (old == null || !wasTrackable || oldStatus != RecordStatus.IN_PROGRESS)
        if (shouldStart && id !in state.activeSessions) {
            val ownTitle = title(note)
            val parentId = entry.parentId
            state = TimeTrackingEngine.start(
                state = state,
                taskId = id,
                parentTaskId = parentId,
                taskTitle = if (parentId == null) ownTitle else entry.parentTitle ?: "Задача",
                subtaskTitle = if (parentId == null) null else ownTitle,
                nowMillis = nowMillis
            )
        }
    }
    return state
}
