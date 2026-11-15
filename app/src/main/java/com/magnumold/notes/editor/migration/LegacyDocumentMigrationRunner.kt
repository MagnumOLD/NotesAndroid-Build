package com.magnumold.notes.editor.migration

import android.content.SharedPreferences
import com.magnumold.notes.PersistenceSnapshot
import com.magnumold.notes.TimeTrackingState

/**
 * Persists the one-time migration completion marker only after every document
 * has been written and read back successfully. An interrupted run is safe to
 * retry because the coordinator is idempotent.
 */
internal class LegacyDocumentMigrationRunner(
    private val preferences: SharedPreferences,
    private val coordinator: LegacyDocumentMigrationCoordinator
) {
    val isComplete: Boolean
        get() = preferences.getInt(KEY_VERSION, 0) >= MIGRATION_VERSION

    fun runIfNeeded(
        snapshot: PersistenceSnapshot,
        timeTracking: TimeTrackingState = TimeTrackingState()
    ): LegacyDocumentMigrationReport? {
        if (isComplete) return null

        val report = coordinator.migrate(snapshot, timeTracking)
        val committed = preferences.edit()
            .putInt(KEY_VERSION, MIGRATION_VERSION)
            .putString(KEY_BACKUP_SHA256, report.backup.sha256)
            .putString(KEY_BACKUP_FINGERPRINT, report.backup.fingerprint)
            .putLong(KEY_COMPLETED_AT, System.currentTimeMillis())
            .commit()
        check(committed) { "Unable to commit document migration marker" }
        return report
    }

    private companion object {
        const val MIGRATION_VERSION = 1
        const val KEY_VERSION = "legacy_document_migration_version"
        const val KEY_BACKUP_SHA256 = "legacy_document_migration_backup_sha256"
        const val KEY_BACKUP_FINGERPRINT = "legacy_document_migration_backup_fingerprint"
        const val KEY_COMPLETED_AT = "legacy_document_migration_completed_at"
    }
}
