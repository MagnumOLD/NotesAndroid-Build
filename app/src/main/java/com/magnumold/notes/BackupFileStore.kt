package com.magnumold.notes

import android.content.ContentResolver
import android.net.Uri

/**
 * Handles the Android document-provider boundary for backup files.
 *
 * JSON encoding/decoding remains in BackupJsonCodec; this class owns only
 * stream access and UTF-8 conversion.
 */
internal class BackupFileStore(
    private val contentResolver: ContentResolver
) {
    fun writeBackup(uri: Uri, snapshot: PersistenceSnapshot, timerStore: TaskTimerStore? = null) {
        val output = contentResolver.openOutputStream(uri)
            ?: error("Не удалось открыть файл")
        output.use {
            val json = createBackupJson(
                snapshot.notes,
                snapshot.collections,
                snapshot.archived,
                snapshot.trash,
                timerStore?.sessions().orEmpty(),
                timerStore?.preferences() ?: TimerPreferences()
            )
            it.write(json.toByteArray(Charsets.UTF_8))
        }
    }

    fun readBackup(uri: Uri): String {
        val input = contentResolver.openInputStream(uri)
            ?: error("Не удалось прочитать файл")
        return input.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
