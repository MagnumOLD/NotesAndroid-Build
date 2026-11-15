package com.magnumold.notes.editor.storage

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import com.magnumold.notes.editor.model.DocumentJsonCodec
import com.magnumold.notes.editor.model.EditorDocument
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

internal data class DocumentStoreSnapshot(
    val documents: List<EditorDocument>,
    val corruptDocumentIds: List<String>
)

/**
 * File-backed persistence boundary for rich documents.
 *
 * Each document and the recovery draft is written through Android AtomicFile.
 * Large JSON payloads therefore never share the legacy notes preferences or
 * require rewriting a monolithic index when one document changes.
 */
internal class DocumentRepository(
    context: Context,
    directoryName: String = DEFAULT_DIRECTORY
) {
    private val directory = File(context.applicationContext.filesDir, directoryName).apply {
        require(directoryName.isNotBlank() && '/' !in directoryName && '\\' !in directoryName)
        if (!exists()) check(mkdirs()) { "Unable to create document directory" }
        check(isDirectory) { "Document storage path is not a directory" }
    }
    private val recoveryFile = AtomicFile(File(directory, RECOVERY_FILE_NAME))

    fun save(document: EditorDocument) {
        writeAtomically(documentFile(document.id), DocumentJsonCodec.encode(document))
        // A successful primary save only clears a byte-for-byte matching draft.
        // A newer or different recovery document is deliberately preserved.
        val draft = loadRecoveryDraft().getOrNull()
        if (draft == document) clearRecoveryDraft()
    }

    fun load(documentId: String): Result<EditorDocument?> = runCatching {
        val file = documentFile(documentId)
        if (!file.baseFile.exists() && !file.baseFile.resolveSibling(file.baseFile.name + ".bak").exists()) {
            return@runCatching null
        }
        val decoded = DocumentJsonCodec.decode(readAtomically(file))
        require(decoded.id == documentId) { "Document id mismatch" }
        decoded
    }

    fun loadAll(): DocumentStoreSnapshot {
        val documents = mutableListOf<EditorDocument>()
        val corruptIds = mutableListOf<String>()
        directory.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(DOCUMENT_EXTENSION) }
            .sortedBy { it.name }
            .forEach { file ->
                val id = decodeDocumentId(file.name.removeSuffix(DOCUMENT_EXTENSION))
                if (id == null) {
                    corruptIds += file.name
                } else {
                    load(id).onSuccess { document ->
                        if (document != null) documents += document else corruptIds += id
                    }.onFailure {
                        // Keep corrupt payloads untouched for recovery/export.
                        corruptIds += id
                    }
                }
            }
        return DocumentStoreSnapshot(documents, corruptIds)
    }

    fun delete(documentId: String) {
        documentFile(documentId).delete()
        val draft = loadRecoveryDraft().getOrNull()
        if (draft?.id == documentId) clearRecoveryDraft()
    }

    fun saveRecoveryDraft(document: EditorDocument) {
        val payload = JSONObject()
            .put("savedAt", System.currentTimeMillis())
            .put("document", JSONObject(DocumentJsonCodec.encode(document)))
            .toString()
        writeAtomically(recoveryFile, payload)
    }

    fun loadRecoveryDraft(): Result<EditorDocument?> = runCatching {
        if (!recoveryFile.baseFile.exists() &&
            !recoveryFile.baseFile.resolveSibling(recoveryFile.baseFile.name + ".bak").exists()
        ) return@runCatching null
        val root = JSONObject(readAtomically(recoveryFile))
        val document = root.getJSONObject("document")
        DocumentJsonCodec.decode(document.toString())
    }

    fun clearRecoveryDraft(documentId: String? = null) {
        if (documentId != null) {
            val draftId = loadRecoveryDraft().getOrNull()?.id
            if (draftId != documentId) return
        }
        recoveryFile.delete()
    }

    internal fun documentFileForTest(documentId: String): File = documentFile(documentId).baseFile

    private fun documentFile(documentId: String): AtomicFile {
        require(documentId.isNotBlank()) { "Document id must not be blank" }
        val encoded = Base64.encodeToString(
            documentId.toByteArray(StandardCharsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return AtomicFile(File(directory, encoded + DOCUMENT_EXTENSION))
    }

    private fun decodeDocumentId(encoded: String): String? = runCatching {
        String(
            Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
            StandardCharsets.UTF_8
        ).takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun readAtomically(file: AtomicFile): String =
        file.openRead().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

    private fun writeAtomically(file: AtomicFile, value: String) {
        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(value.toByteArray(StandardCharsets.UTF_8))
            stream.flush()
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (error: Throwable) {
            if (stream != null) file.failWrite(stream)
            throw error
        }
    }

    private companion object {
        const val DEFAULT_DIRECTORY = "editor_documents"
        const val DOCUMENT_EXTENSION = ".json"
        const val RECOVERY_FILE_NAME = "recovery-draft.json"
    }
}
