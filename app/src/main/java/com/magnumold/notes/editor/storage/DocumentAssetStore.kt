package com.magnumold.notes.editor.storage

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.io.InputStream
import java.util.UUID

internal data class StoredDocumentAsset(
    val assetId: String,
    val file: File,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long
)

/**
 * App-private storage for document attachments.
 *
 * Imported content is copied out of the provider's URI so documents never depend
 * on temporary URI permissions or the continued availability of the source app.
 */
internal class DocumentAssetStore(
    context: Context,
    private val maxAssetBytes: Long = DEFAULT_MAX_ASSET_BYTES
) {
    private val resolver = context.applicationContext.contentResolver
    private val directory = File(context.applicationContext.filesDir, ASSET_DIRECTORY).apply {
        if (!exists()) check(mkdirs()) { "Unable to create document asset directory" }
    }

    init {
        require(maxAssetBytes > 0) { "Maximum asset size must be positive" }
    }

    fun import(uri: Uri, fileName: String): StoredDocumentAsset {
        require(fileName.isNotBlank()) { "Asset file name must not be blank" }
        val mimeType = resolver.getType(uri)
        val stream = resolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Unable to open selected file")
        return stream.use { import(it, fileName, mimeType) }
    }

    internal fun import(
        input: InputStream,
        fileName: String,
        mimeType: String? = null
    ): StoredDocumentAsset {
        require(fileName.isNotBlank()) { "Asset file name must not be blank" }
        val id = UUID.randomUUID().toString()
        val destination = assetFile(id)
        var copied = 0L
        try {
            input.use { source ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= maxAssetBytes) { "Selected file exceeds the allowed size" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            return StoredDocumentAsset(id, destination, fileName, mimeType, copied)
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    fun find(assetId: String): File? =
        assetFileOrNull(assetId)?.takeIf { it.isFile }

    fun open(assetId: String) = (find(assetId)
        ?: throw IllegalArgumentException("Document asset not found: $assetId")).inputStream()

    fun delete(assetId: String): Boolean = assetFileOrNull(assetId)?.delete() ?: false

    /** Removes files not referenced by any live, archived, or trashed document. */
    fun prune(referencedAssetIds: Set<String>): Int {
        var removed = 0
        directory.listFiles().orEmpty().forEach { file ->
            val id = file.name.removeSuffix(ASSET_EXTENSION)
            if (id !in referencedAssetIds && file.isFile && file.delete()) removed++
        }
        return removed
    }

    private fun assetFile(assetId: String): File =
        assetFileOrNull(assetId) ?: throw IllegalArgumentException("Invalid document asset id")

    private fun assetFileOrNull(assetId: String): File? {
        val parsed = runCatching { UUID.fromString(assetId) }.getOrNull() ?: return null
        if (parsed.toString() != assetId.lowercase()) return null
        return File(directory, "$assetId$ASSET_EXTENSION")
    }

    private companion object {
        const val ASSET_DIRECTORY = "editor_assets"
        const val ASSET_EXTENSION = ".asset"
        const val DEFAULT_MAX_ASSET_BYTES = 100L * 1024L * 1024L
    }
}
