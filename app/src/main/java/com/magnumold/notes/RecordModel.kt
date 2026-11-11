package com.magnumold.notes

internal enum class RecordType {
    NOTE,
    TASK
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
}
