package com.magnumold.notes.editor.storage

import com.magnumold.notes.editor.model.AttachmentBlock
import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.model.ImageBlock
import com.magnumold.notes.editor.model.plainText
import java.util.UUID

internal data class RichDocumentRestoreReport(
    val importedDocuments: Int,
    val remappedDocumentIds: Map<String, String>,
    val remappedAssetIds: Map<String, String>
)

/**
 * Installs a previously validated archive without replacing any live document
 * or asset. Matching documents are retained; conflicting IDs are remapped to
 * fresh note IDs so the legacy note list and rich-document store stay aligned.
 */
internal class RichDocumentBackupRestorer(
    private val documents: DocumentRepository,
    private val assets: DocumentAssetStore
) {
    fun restore(
        payload: RichDocumentBackupArchive.Payload,
        reservedNoteIds: Set<Long> = emptySet(),
        incomingNoteIds: Set<Long> = emptySet()
    ): RichDocumentRestoreReport {
        val reservedIds = (reservedNoteIds + incomingNoteIds).toMutableSet()
        val pending = mutableListOf<Pair<EditorDocument, String>>()
        val remappedDocuments = linkedMapOf<String, String>()

        payload.documents.forEach { source ->
            val existingResult = documents.load(source.id)
            val existing = existingResult.getOrNull()
            val isConflict = existingResult.isFailure || existing != null
            if (existing != null && existing.plainText() == source.plainText()) {
                // Same note already exists. Keep its local formatting and assets.
                return@forEach
            }

            val targetId = if (!isConflict && source.id.toLongOrNull()?.let { it in reservedNoteIds } != true) {
                source.id
            } else {
                newDocumentId(source.id, reservedIds)
            }
            if (targetId != source.id) remappedDocuments[source.id] = targetId
            pending += source to targetId
        }

        val requiredAssetIds = pending.flatMap { (document, _) ->
            document.blocks.mapNotNull { block ->
                when (block) {
                    is ImageBlock -> block.assetId
                    is AttachmentBlock -> block.assetId
                    else -> null
                }
            }
        }.toSet()
        val stagedAssets = payload.assets.associateBy { it.assetId }
        require(requiredAssetIds.all { it in stagedAssets }) { "A document asset is missing from the backup" }

        val installedAssets = linkedMapOf<String, String>()
        val savedDocumentIds = mutableListOf<String>()
        try {
            requiredAssetIds.forEach { oldId ->
                val staged = stagedAssets.getValue(oldId)
                val installed = assets.importBackupAsset(staged.file, oldId)
                installedAssets[oldId] = installed.assetId
            }

            pending.forEach { (source, targetId) ->
                val rewrittenBlocks = source.blocks.map { block ->
                    when (block) {
                        is ImageBlock -> block.copy(assetId = installedAssets.getValue(block.assetId))
                        is AttachmentBlock -> block.copy(assetId = installedAssets.getValue(block.assetId))
                        else -> block
                    }
                }
                documents.save(source.copy(id = targetId, blocks = rewrittenBlocks))
                savedDocumentIds += targetId
            }

            return RichDocumentRestoreReport(
                importedDocuments = savedDocumentIds.size,
                remappedDocumentIds = remappedDocuments,
                remappedAssetIds = installedAssets
            )
        } catch (error: Throwable) {
            savedDocumentIds.forEach { id -> runCatching { documents.delete(id) } }
            installedAssets.values.forEach { id -> runCatching { assets.delete(id) } }
            throw error
        }
    }

    private fun newDocumentId(sourceId: String, reservedIds: MutableSet<Long>): String {
        if (sourceId.toLongOrNull() != null) {
            var candidate = System.currentTimeMillis()
            while (candidate in reservedIds || documents.load(candidate.toString()).let {
                    it.isFailure || it.getOrNull() != null
                }
            ) candidate++
            reservedIds += candidate
            return candidate.toString()
        }
        while (true) {
            val candidate = UUID.randomUUID().toString()
            val existing = documents.load(candidate)
            if (existing.isSuccess && existing.getOrNull() == null) return candidate
        }
    }
}
