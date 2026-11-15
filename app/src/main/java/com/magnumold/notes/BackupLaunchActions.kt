package com.magnumold.notes

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.magnumold.notes.editor.model.AttachmentBlock
import com.magnumold.notes.editor.model.ImageBlock
import com.magnumold.notes.editor.storage.DocumentAssetStore
import com.magnumold.notes.editor.storage.DocumentRepository
import com.magnumold.notes.editor.storage.RichDocumentBackupRestorer
import java.io.File
import java.util.UUID

internal data class BackupLaunchActions(
    val exportBackup: () -> Unit,
    val importBackup: () -> Unit
)

/**
 * Owns the document-picker lifecycle and user feedback for both legacy JSON
 * backups and complete ZIP backups containing rich documents and assets.
 */
@Composable
internal fun rememberBackupActions(
    activity: MainActivity,
    snapshot: PersistenceSnapshot,
    recordsViewModel: NotesViewModel,
    backupFileStore: BackupFileStore,
    documentRepository: DocumentRepository,
    documentAssetStore: DocumentAssetStore
): BackupLaunchActions {
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            try {
                val stored = documentRepository.loadAll()
                check(stored.corruptDocumentIds.isEmpty()) {
                    "Есть повреждённые документы. Сначала восстановите их."
                }
                val assetIds = stored.documents.flatMap { document ->
                    document.blocks.mapNotNull { block ->
                        when (block) {
                            is ImageBlock -> block.assetId
                            is AttachmentBlock -> block.assetId
                            else -> null
                        }
                    }
                }.toSet()
                val assetFiles = assetIds.associateWith { id ->
                    documentAssetStore.find(id)
                        ?: error("Не найден файл вложения документа: $id")
                }
                backupFileStore.writeRichBackup(
                    uri = uri,
                    snapshot = snapshot,
                    documents = stored.documents,
                    assetFiles = assetFiles
                )
                Toast.makeText(activity, "Полная резервная копия сохранена", Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                Toast.makeText(
                    activity,
                    error.message ?: "Не удалось сохранить резервную копию",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val staging = File(activity.cacheDir, "backup-import-${UUID.randomUUID()}")
            try {
                if (backupFileStore.isRichBackup(uri)) {
                    val payload = backupFileStore.readRichBackup(uri, staging)
                    // Validate the legacy snapshot before installing any rich data.
                    val legacy = parseBackupJson(payload.legacySnapshotJson)
                    val report = RichDocumentBackupRestorer(
                        documentRepository,
                        documentAssetStore
                    ).restore(payload)
                    val importedRecords = recordsViewModel.importBackupData(legacy)
                    Toast.makeText(
                        activity,
                        "Импортировано записей: $importedRecords, документов: ${report.importedDocuments}",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val importedCount = recordsViewModel.importBackup(backupFileStore.readBackup(uri))
                    Toast.makeText(
                        activity,
                        "Импортировано заметок: $importedCount",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (error: Exception) {
                Toast.makeText(
                    activity,
                    error.message ?: "Не удалось импортировать резервную копию",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                staging.deleteRecursively()
            }
        }
    }
    return BackupLaunchActions(
        exportBackup = { exportLauncher.launch("Notes-backup.zip") },
        importBackup = {
            importLauncher.launch(
                arrayOf("application/zip", "application/x-zip-compressed", "application/json", "text/json", "text/plain", "application/octet-stream")
            )
        }
    )
}
