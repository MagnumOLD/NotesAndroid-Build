package com.magnumold.notes.editor.storage

import com.magnumold.notes.editor.model.AttachmentBlock
import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.model.ImageBlock
import java.util.UUID

internal data class RichDocumentRestoreReport(
    val importedDocuments: Int,
    val remappedDocumentIds: Map<String, String>,
    val remappedAssetIds: Map<String, String>
)

/**
 * Installs a previously validated archive without replacing any live document
 * or asset. Every imported asset receives a fresh private-store ID; document
 * references are rewritten before atomic document writes.
 */
internal class RichDocumentBackupRestorer(
    private val documents: DocumentRepository,
    private val assets: DocumentAssetStore
) {
    fun restore(payload: RichDocumentBackupArchive.Payload): RichDocumentRestoreReport {
        val installedAssets = linkedMapOf<String, String>()
        val savedDocumentIds = mutableListOf<String>()
        try {
            payload.assets.forEach { staged ->
                val installed = assets.importBackupAsset(staged.file, "backup-asset")
                installedAssets[staged.assetId] = installed.assetId
            }

            val remappedDocuments = linkedMapOf<String, String>()
            payload.documents.forEach { source ->
                val existing = documents.load(source.id)
                val targetId = if (existing.isSuccess && existing.getOrNull() == null) {
                    source.id
                } else {
                    newDocumentId()
                }
                remappedDocuments[source.id] = targetId
                val rewrittenBlocks = source.blocks.map { block ->
                    when (block) {
                        is ImageBlock -> block.copy(assetId = installedAssets.getValue(block.assetId))
                        is AttachmentBlock -> block.copy(assetId = installedAssets.getValue(block.assetId))
                        else -> block
                    }
                }
                val rewritten = source.copy(id = targetId, blocks = rewrittenBlocks)
                documents.save(rewritten)
                savedDocumentIds += targetId
            }

            return RichDocumentRestoreReport(
                importedDocuments = savedDocumentIds.size,
                remappedDocumentIds = remappedDocuments.filter { it.key != it.value },
                remappedAssetIds = installedAssets
            )
        } catch (error: Throwable) {
            savedDocumentIds.forEach { id -> runCatching { documents.delete(id) } }
            installedAssets.values.forEach { id -> runCatching { assets.delete(id) } }
            throw error
        }
    }

    private fun newDocumentId(): String {
        while (true) {
            val candidate = UUID.randomUUID().toString()
            val existing = documents.load(candidate)
            if (existing.isSuccess && existing.getOrNull() == null) return candidate
        }
    }
}
