package com.magnumold.notes.editor.storage

import android.content.SharedPreferences
import android.util.Base64
import com.magnumold.notes.editor.model.DocumentJsonCodec
import com.magnumold.notes.editor.model.EditorDocument
import org.json.JSONArray

internal data class DocumentStoreSnapshot(
    val documents: List<EditorDocument>,
    val corruptDocumentIds: List<String>
)

/**
 * Isolated persistence boundary for rich documents.
 *
 * The legacy notes snapshot is deliberately not touched here. Document payloads,
 * the index and recovery draft use dedicated preference keys so task records and
 * the existing backup format remain unchanged during the editor rollout.
 */
internal class DocumentRepository(
    private val preferences: SharedPreferences
) {
    fun save(document: EditorDocument) {
        val ids = readIndex()
        val nextIds = (ids.filterNot { it == document.id } + document.id)
        preferences.edit()
            .putString(documentKey(document.id), DocumentJsonCodec.encode(document))
            .putString(KEY_DOCUMENT_INDEX, JSONArray(nextIds).toString())
            .apply()
    }

    fun load(documentId: String): Result<EditorDocument?> = runCatching {
        val raw = preferences.getString(documentKey(documentId), null) ?: return@runCatching null
        DocumentJsonCodec.decode(raw).also { require(it.id == documentId) { "Document id mismatch" } }
    }

    fun loadAll(): DocumentStoreSnapshot {
        val documents = mutableListOf<EditorDocument>()
        val corruptIds = mutableListOf<String>()
        readIndex().forEach { id ->
            val result = load(id)
            result.onSuccess { document ->
                if (document != null) documents += document else corruptIds += id
            }.onFailure {
                // Keep the raw payload and index entry intact for recovery.
                corruptIds += id
            }
        }
        return DocumentStoreSnapshot(documents, corruptIds)
    }

    fun delete(documentId: String) {
        val nextIds = readIndex().filterNot { it == documentId }
        preferences.edit()
            .remove(documentKey(documentId))
            .putString(KEY_DOCUMENT_INDEX, JSONArray(nextIds).toString())
            .apply()
    }

    fun saveRecoveryDraft(document: EditorDocument) {
        preferences.edit()
            .putString(KEY_RECOVERY_DRAFT, DocumentJsonCodec.encode(document))
            .apply()
    }

    fun loadRecoveryDraft(): Result<EditorDocument?> = runCatching {
        val raw = preferences.getString(KEY_RECOVERY_DRAFT, null) ?: return@runCatching null
        DocumentJsonCodec.decode(raw)
    }

    fun clearRecoveryDraft() {
        preferences.edit().remove(KEY_RECOVERY_DRAFT).apply()
    }

    private fun readIndex(): List<String> = runCatching {
        val raw = preferences.getString(KEY_DOCUMENT_INDEX, "[]") ?: "[]"
        val json = JSONArray(raw)
        (0 until json.length()).map { json.getString(it) }.distinct()
    }.getOrDefault(emptyList())

    private fun documentKey(documentId: String): String {
        val encoded = Base64.encodeToString(
            documentId.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return "$KEY_DOCUMENT_PREFIX$encoded"
    }

    private companion object {
        const val KEY_DOCUMENT_INDEX = "editor_documents_v1_index"
        const val KEY_DOCUMENT_PREFIX = "editor_document_v1_"
        const val KEY_RECOVERY_DRAFT = "editor_recovery_draft_v1"
    }
}
