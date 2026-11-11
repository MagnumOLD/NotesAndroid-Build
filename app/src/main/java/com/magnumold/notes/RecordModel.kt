package com.magnumold.notes

/**
 * The three record kinds shown by the app.
 * NOTE is free text, TASK has its own status, UNIVERSAL owns nested records.
 */
internal enum class RecordType {
    NOTE,
    TASK,
    UNIVERSAL
}

internal enum class RecordStatus {
    WAITING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    MISSED
}

internal enum class RecordDateMode {
    DATE,
    DEADLINE
}

/**
 * Date/status rules shared by editors and persistence.
 *
 * A deadline may turn an unfinished task into MISSED. Moving the date later
 * never resumes a missed task: that is an explicit user action.
 */
internal object RecordStatusRules {
    fun applyDeadline(
        status: RecordStatus,
        dateMode: RecordDateMode?,
        dateAt: Long?,
        now: Long
    ): RecordStatus {
        if (status == RecordStatus.COMPLETED || status == RecordStatus.CANCELLED ||
            status == RecordStatus.MISSED
        ) return status

        if (dateMode != RecordDateMode.DEADLINE || dateAt == null) return status
        return if (now >= dateAt + 86_400_000L) RecordStatus.MISSED else status
    }

    /**
     * Derives a universal record's status from its descendants.
     * Completed and cancelled children are resolved; a missed child remains
     * visible as an unresolved outcome and therefore propagates MISSED.
     */
    fun deriveUniversalStatus(children: List<RecordNode>): RecordStatus {
        if (children.isEmpty()) return RecordStatus.WAITING

        val statuses = children.map { child ->
            if (child.type == RecordType.UNIVERSAL) {
                deriveUniversalStatus(child.children)
            } else {
                child.status
            }
        }

        return when {
            statuses.any { it == RecordStatus.MISSED } -> RecordStatus.MISSED
            statuses.any { it == RecordStatus.IN_PROGRESS } -> RecordStatus.IN_PROGRESS
            statuses.any { it == RecordStatus.WAITING } -> RecordStatus.WAITING
            else -> RecordStatus.COMPLETED
        }
    }

    fun canContain(parent: RecordType, child: RecordType): Boolean =
        parent == RecordType.UNIVERSAL &&
            (child == RecordType.TASK || child == RecordType.UNIVERSAL)
}

/**
 * UI-independent representation used by the new record editor and nested list.
 * The existing Note model remains the persisted legacy adapter during migration.
 */
internal data class RecordNode(
    val id: Long,
    val type: RecordType,
    val status: RecordStatus = RecordStatus.WAITING,
    val dateMode: RecordDateMode? = null,
    val dateAt: Long? = null,
    val children: List<RecordNode> = emptyList()
)
