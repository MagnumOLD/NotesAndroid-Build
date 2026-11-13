package com.magnumold.notes

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

internal data class BackupLaunchActions(
    val exportBackup: () -> Unit,
    val importBackup: () -> Unit
)

/**
 * Owns the document-picker lifecycle and user feedback for backup operations.
 * File streams and JSON conversion remain in their dedicated layers.
 */
@Composable
internal fun rememberBackupActions(
    activity: MainActivity,
    snapshot: PersistenceSnapshot,
    recordsViewModel: NotesViewModel,
    backupFileStore: BackupFileStore
): BackupLaunchActions {
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                backupFileStore.writeBackup(uri, snapshot)
                Toast.makeText(activity, "Резервная копия сохранена", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(activity, "Не удалось сохранить резервную копию", Toast.LENGTH_LONG).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val json = backupFileStore.readBackup(uri)
                val importedCount = recordsViewModel.importBackup(json)
                Toast.makeText(activity, "Импортировано заметок: $importedCount", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(activity, "Не удалось импортировать файл: неверный формат", Toast.LENGTH_LONG).show()
            }
        }
    }
    return BackupLaunchActions(
        exportBackup = { exportLauncher.launch("Notes-backup.json") },
        importBackup = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) }
    )
}
