package com.magnumold.notes.editor.storage

import com.magnumold.notes.editor.model.AttachmentBlock
import com.magnumold.notes.editor.model.DocumentJsonCodec
import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.model.ImageBlock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import android.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * ZIP container for the legacy snapshot plus rich documents and binary assets.
 *
 * Parsing is staged into a private temporary directory. No live document or
 * asset is modified by this class; callers must validate the complete payload
 * before committing it to the app stores.
 */
internal object RichDocumentBackupArchive {
    private const val MANIFEST = "manifest.json"
    private const val LEGACY_SNAPSHOT = "legacy-snapshot.json"
    private const val DOCUMENT_PREFIX = "documents/"
    private const val ASSET_PREFIX = "assets/"
    private const val MAX_ENTRY_BYTES = 100L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 1024L * 1024L * 1024L

    internal data class StagedAsset(val assetId: String, val file: File)

    internal data class Payload(
        val legacySnapshotJson: String,
        val documents: List<EditorDocument>,
        val assets: List<StagedAsset>
    )

    fun write(
        output: OutputStream,
        legacySnapshotJson: String,
        documents: List<EditorDocument>,
        assetFiles: Map<String, File>
    ) {
        require(documents.map { it.id }.toSet().size == documents.size) {
            "Duplicate document ids in backup"
        }
        val referencedAssets = referencedAssetIds(documents)
        require(referencedAssets.all { assetFiles[it]?.isFile == true }) {
            "A document refers to a missing asset"
        }
        val selectedAssets = referencedAssets.sorted()
        val documentEntries = documents.sortedBy { it.id }.map { document ->
            encodedId(document.id) + ".json"
        }
        val manifest = JSONObject()
            .put("format", "notes_rich_backup")
            .put("version", 1)
            .put("documents", JSONArray(documents.sortedBy { it.id }.map { it.id }))
            .put("assets", JSONArray(selectedAssets))
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(LEGACY_SNAPSHOT))
            zip.write(legacySnapshotJson.toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toString().toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            documents.sortedBy { it.id }.forEachIndexed { index, document ->
                zip.putNextEntry(ZipEntry(DOCUMENT_PREFIX + documentEntries[index]))
                zip.write(DocumentJsonCodec.encode(document).toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
            selectedAssets.forEach { id ->
                zip.putNextEntry(ZipEntry(ASSET_PREFIX + "$id.asset"))
                assetFiles.getValue(id).inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.finish()
        }
    }

    fun read(input: InputStream, stagingDirectory: File): Payload {
        require(stagingDirectory.isDirectory || stagingDirectory.mkdirs()) {
            "Unable to create backup staging directory"
        }
        val entries = linkedMapOf<String, File>()
        var totalBytes = 0L
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory) { "Directory entries are not allowed in backup" }
                    val name = entry.name
                    require(name == MANIFEST || name == LEGACY_SNAPSHOT ||
                        name.startsWith(DOCUMENT_PREFIX) || name.startsWith(ASSET_PREFIX)) {
                        "Unexpected backup entry"
                    }
                    require(!name.contains("..") && !name.startsWith("/") && '\\' !in name) {
                        "Invalid backup entry path"
                    }
                    require(name !in entries) { "Duplicate backup entry" }
                    val destination = File(stagingDirectory, "entry-${entries.size}.tmp")
                    var entryBytes = 0L
                    destination.outputStream().buffered().use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            entryBytes += count
                            totalBytes += count
                            require(entryBytes <= MAX_ENTRY_BYTES && totalBytes <= MAX_TOTAL_BYTES) {
                                "Backup archive exceeds size limits"
                            }
                            out.write(buffer, 0, count)
                        }
                    }
                    entries[name] = destination
                    zip.closeEntry()
                }
            }

            val manifestFile = entries[MANIFEST] ?: error("Backup manifest is missing")
            val legacyFile = entries[LEGACY_SNAPSHOT] ?: error("Legacy snapshot is missing")
            val manifest = JSONObject(manifestFile.readText(StandardCharsets.UTF_8))
            require(manifest.optString("format") == "notes_rich_backup" &&
                manifest.optInt("version") == 1) { "Unsupported rich backup format" }

            val documentIds = manifest.getJSONArray("documents")
            val documents = (0 until documentIds.length()).map { index ->
                val id = documentIds.getString(index)
                val file = entries[DOCUMENT_PREFIX + encodedId(id) + ".json"]
                    ?: error("Document payload is missing")
                DocumentJsonCodec.decode(file.readText(StandardCharsets.UTF_8)).also {
                    require(it.id == id) { "Document id does not match manifest" }
                }
            }
            require(documents.map { it.id }.toSet().size == documents.size) {
                "Duplicate document ids in manifest"
            }

            val assetIdsJson = manifest.getJSONArray("assets")
            val assets = (0 until assetIdsJson.length()).map { index ->
                val id = assetIdsJson.getString(index)
                require(isUuid(id)) { "Invalid asset id in manifest" }
                val file = entries[ASSET_PREFIX + "$id.asset"]
                    ?: error("Asset payload is missing")
                StagedAsset(id, file)
            }
            require(assets.map { it.assetId }.toSet().size == assets.size) {
                "Duplicate asset ids in manifest"
            }
            val expectedAssets = referencedAssetIds(documents)
            require(assets.map { it.assetId }.toSet() == expectedAssets) {
                "Backup contains missing or unreferenced assets"
            }
            val expectedEntries = buildSet {
                add(MANIFEST)
                add(LEGACY_SNAPSHOT)
                documents.forEach { add(DOCUMENT_PREFIX + encodedId(it.id) + ".json") }
                assets.forEach { add(ASSET_PREFIX + it.assetId + ".asset") }
            }
            require(entries.keys == expectedEntries) { "Backup contains unlisted payload entries" }

            return Payload(
                legacySnapshotJson = legacyFile.readText(StandardCharsets.UTF_8),
                documents = documents,
                assets = assets
            )
        } catch (error: Throwable) {
            stagingDirectory.listFiles().orEmpty().forEach { it.delete() }
            throw error
        }
    }

    private fun referencedAssetIds(documents: List<EditorDocument>): Set<String> =
        documents.flatMap { document ->
            document.blocks.mapNotNull { block ->
                when (block) {
                    is ImageBlock -> block.assetId
                    is AttachmentBlock -> block.assetId
                    else -> null
                }
            }
        }.toSet()

    private fun encodedId(id: String): String =
        Base64.encodeToString(id.toByteArray(StandardCharsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun isUuid(value: String): Boolean =
        runCatching { java.util.UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)
}
