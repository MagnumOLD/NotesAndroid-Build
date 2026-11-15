package com.magnumold.notes

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.magnumold.notes.editor.model.AttachmentBlock
import com.magnumold.notes.editor.model.ImageBlock
import com.magnumold.notes.editor.storage.DocumentAssetStore
import com.magnumold.notes.editor.storage.DocumentRepository
import com.magnumold.notes.editor.storage.RichDocumentBackupRestorer
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
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
                    }
                }
                result.onSuccess {
                    Toast.makeText(activity, "Полная резервная копия сохранена", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(
                        activity,
                        error.message ?: "Не удалось сохранить резервную копию",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val staging = File(activity.cacheDir, "backup-import-${UUID.randomUUID()}")
                val result = withContext(Dispatchers.IO) {
                    try {
                        runCatching {
                        if (backupFileStore.isRichBackup(uri)) {
                            val payload = backupFileStore.readRichBackup(uri, staging)
                            // Validate the legacy snapshot before installing any rich data.
                            val legacy = parseBackupJson(payload.legacySnapshotJson)
                            fun collectIds(roots: List<Note>): Set<Long> {
                                val ids = mutableSetOf<Long>()
                                fun visit(note: Note) {
                                    ids += note.id
                                    note.children.forEach(::visit)
                                }
                                roots.forEach(::visit)
                                return ids
                            }
                            val currentIds = collectIds(
                                snapshot.notes +
                                    snapshot.collections.flatMap { it.notes } +
                                    snapshot.archived.map { it.note } +
                                    snapshot.trash.map { it.note }
                            )
                            val incomingIds = collectIds(
                                legacy.notes +
                                    legacy.collections.flatMap { it.notes } +
                                    legacy.archived.map { it.note } +
                                    legacy.trash.map { it.note }
                            )
                            val report = RichDocumentBackupRestorer(
                                documentRepository,
                                documentAssetStore
                            ).restore(
                                payload,
                                reservedNoteIds = currentIds,
                                incomingNoteIds = incomingIds
                            )
                            val importedRecords = recordsViewModel.importBackupData(
                                legacy.remapNoteIds(report.remappedDocumentIds)
                            )
                            "Импортировано записей: $importedRecords, документов: ${report.importedDocuments}"
                        } else {
                            val importedCount = recordsViewModel.importBackup(backupFileStore.readBackup(uri))
                            "Импортировано заметок: $importedCount"
                        }
                        }
                    } finally {
                        staging.deleteRecursively()
                    }
                }
                result.onSuccess { message ->
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(
                        activity,
                        error.message ?: "Не удалось импортировать резервную копию",
                        Toast.LENGTH_LONG
                    ).show()
                }
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
