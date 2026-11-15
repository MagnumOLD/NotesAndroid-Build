package com.magnumold.notes

import android.content.ContentResolver
import android.net.Uri
import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.storage.RichDocumentBackupArchive
import java.io.File

/**
 * Handles the Android document-provider boundary for backup files.
 *
 * JSON encoding/decoding remains in BackupJsonCodec; this class owns only
 * stream access and UTF-8 conversion.
 */
internal class BackupFileStore(
    private val contentResolver: ContentResolver,
    private val timeTrackingStateProvider: () -> TimeTrackingState = { TimeTrackingState() }
) {
    fun writeBackup(uri: Uri, snapshot: PersistenceSnapshot, timeTracking: TimeTrackingState = timeTrackingStateProvider()) {
        val output = contentResolver.openOutputStream(uri)
            ?: error("Не удалось открыть файл")
        output.use {
            val json = createBackupJson(
                snapshot.notes,
                snapshot.collections,
                snapshot.archived,
                snapshot.trash,
                timeTracking
            )
            it.write(json.toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * Writes the extended ZIP format. The caller supplies the current rich
     * documents and only the asset files referenced by those documents.
     * Legacy JSON export remains unchanged for compatibility.
     */
    fun writeRichBackup(
        uri: Uri,
        snapshot: PersistenceSnapshot,
        documents: List<EditorDocument>,
        assetFiles: Map<String, File>,
        timeTracking: TimeTrackingState = timeTrackingStateProvider()
    ) {
        val output = contentResolver.openOutputStream(uri)
            ?: error("Не удалось открыть файл")
        val legacyJson = createBackupJson(
            snapshot.notes, snapshot.collections, snapshot.archived, snapshot.trash, timeTracking
        )
        RichDocumentBackupArchive.write(output, legacyJson, documents, assetFiles)
    }

    /** Parses and validates a ZIP backup into staging files without mutating live stores. */
    fun readRichBackup(uri: Uri, stagingDirectory: File): RichDocumentBackupArchive.Payload {
        val input = contentResolver.openInputStream(uri)
            ?: error("Не удалось прочитать файл")
        return RichDocumentBackupArchive.read(input, stagingDirectory)
    }

    fun isRichBackup(uri: Uri): Boolean {
        val input = contentResolver.openInputStream(uri)
            ?: error("Не удалось прочитать файл")
        return input.use {
            val header = ByteArray(4)
            val count = it.read(header)
            count >= 4 && header[0] == 'P'.code.toByte() &&
                header[1] == 'K'.code.toByte() &&
                header[2] == 3.toByte() && header[3] == 4.toByte()
        }
    }

    fun readBackup(uri: Uri): String {
        val input = contentResolver.openInputStream(uri)
            ?: error("Не удалось прочитать файл")
        return input.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
