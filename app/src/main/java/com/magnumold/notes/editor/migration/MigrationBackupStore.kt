package com.magnumold.notes.editor.migration

import android.content.Context
import android.util.AtomicFile
import com.magnumold.notes.PersistenceSnapshot
import com.magnumold.notes.TimeTrackingState
import com.magnumold.notes.createBackupJson
import com.magnumold.notes.parseBackupJson
import org.json.JSONObject
import java.io.FileOutputStream
import java.security.MessageDigest

internal data class MigrationBackupInfo(
    val fileName: String,
    val sha256: String,
    val fingerprint: String
)

/**
 * Keeps a verified, app-private copy of the legacy record snapshot before
 * one-way document migration. The first valid backup is never overwritten.
 */
internal class MigrationBackupStore(
    context: Context,
    directoryName: String = DEFAULT_DIRECTORY
) {
    private val directory = java.io.File(context.applicationContext.filesDir, directoryName).apply {
        require(directoryName.isNotBlank() && '/' !in directoryName && '\\' !in directoryName)
        if (!exists()) check(mkdirs()) { "Unable to create migration backup directory" }
        check(isDirectory) { "Migration backup path is not a directory" }
    }
    private fun backupFile(fingerprint: String) =
        AtomicFile(java.io.File(directory, "$BACKUP_FILE_PREFIX$fingerprint.json"))

    fun ensureBackup(
        snapshot: PersistenceSnapshot,
        timeTracking: TimeTrackingState = TimeTrackingState()
    ): MigrationBackupInfo {
        val currentPayload = createBackupJson(
            snapshot.notes, snapshot.collections, snapshot.archived, snapshot.trash, timeTracking
        )
        val currentFingerprint = fingerprint(currentPayload)

        val targetFile = backupFile(currentFingerprint)
        if (targetFile.baseFile.exists() ||
            targetFile.baseFile.resolveSibling(targetFile.baseFile.name + ".bak").exists()
        ) {
            val existing = readVerifiedEnvelope(targetFile).second
            require(existing.fingerprint == currentFingerprint) {
                "Migration backup fingerprint mismatch"
            }
            return existing
        }

        // Parse before writing so an invalid export can never become the only backup.
        parseBackupJson(currentPayload)
        val sha = sha256(currentPayload)
        val envelope = JSONObject()
            .put("format", BACKUP_FORMAT)
            .put("version", BACKUP_VERSION)
            .put("sha256", sha)
            .put("fingerprint", currentFingerprint)
            .put("payload", currentPayload)
            .toString()

        writeAtomically(targetFile, envelope)
        val verified = readVerifiedEnvelope(targetFile).second
        require(verified.sha256 == sha && verified.fingerprint == currentFingerprint) {
            "Migration backup verification failed"
        }
        return verified
    }

    fun readVerifiedBackup(): MigrationBackupInfo = readVerifiedEnvelope(latestBackupFile()).second

    /** Returns the newest verified versioned snapshot after checksum and schema validation. */
    fun readBackupPayload(): String = readVerifiedEnvelope(latestBackupFile()).first

    private fun latestBackupFile(): AtomicFile {
        val file = directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(BACKUP_FILE_PREFIX) && it.name.endsWith(".json") }
            .maxByOrNull { it.lastModified() }
            ?: error("Migration backup does not exist")
        return AtomicFile(file)
    }

    private fun readVerifiedEnvelope(file: AtomicFile): Pair<String, MigrationBackupInfo> {
        val root = JSONObject(readAtomically(file))
        require(root.getString("format") == BACKUP_FORMAT) { "Invalid migration backup format" }
        require(root.getInt("version") == BACKUP_VERSION) { "Unsupported migration backup version" }
        val payload = root.getString("payload")
        val expectedSha = root.getString("sha256")
        val expectedFingerprint = root.getString("fingerprint")
        require(sha256(payload) == expectedSha) { "Migration backup checksum mismatch" }
        parseBackupJson(payload)
        require(fingerprint(payload) == expectedFingerprint) { "Migration backup fingerprint mismatch" }
        return payload to MigrationBackupInfo(file.baseFile.name, expectedSha, expectedFingerprint)
    }

    private fun fingerprint(payload: String): String {
        val root = JSONObject(payload)
        root.remove("exportedAt")
        return sha256(root.toString())
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun readAtomically(file: AtomicFile): String =
        file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun writeAtomically(file: AtomicFile, value: String) {
        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(value.toByteArray(Charsets.UTF_8))
            stream.flush()
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (error: Throwable) {
            if (stream != null) file.failWrite(stream)
            throw error
        }
    }

    private companion object {
        const val DEFAULT_DIRECTORY = "editor_migration_backup"
        const val BACKUP_FILE_PREFIX = "legacy-records-backup-"
        const val BACKUP_FORMAT = "notes_legacy_migration_backup"
        const val BACKUP_VERSION = 1
    }
}
