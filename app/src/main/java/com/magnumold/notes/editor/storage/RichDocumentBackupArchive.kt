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
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
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
    private const val MAX_ARCHIVE_BYTES = MAX_TOTAL_BYTES + 64L * 1024L * 1024L

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
        val serializedDocuments = documents.sortedBy { it.id }.map { document ->
            document to DocumentJsonCodec.encode(document).toByteArray(StandardCharsets.UTF_8)
        }
        val legacyBytes = legacySnapshotJson.toByteArray(StandardCharsets.UTF_8)
        require(legacyBytes.size <= MAX_ENTRY_BYTES) { "Legacy snapshot exceeds backup entry size limit" }
        require(serializedDocuments.all { it.second.size <= MAX_ENTRY_BYTES }) {
            "A document exceeds backup entry size limit"
        }
        require(selectedAssets.all { assetFiles.getValue(it).length() <= MAX_ENTRY_BYTES }) {
            "An asset exceeds backup entry size limit"
        }
        val totalBytes = legacyBytes.size.toLong() +
            serializedDocuments.sumOf { it.second.size.toLong() } +
            selectedAssets.sumOf { assetFiles.getValue(it).length() }
        require(totalBytes <= MAX_TOTAL_BYTES) { "Backup exceeds total uncompressed size limit" }
        val manifest = JSONObject()
            .put("format", "notes_rich_backup")
            .put("version", 1)
            .put("documents", JSONArray(documents.sortedBy { it.id }.map { it.id }))
            .put("assets", JSONArray(selectedAssets))
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(LEGACY_SNAPSHOT))
            zip.write(legacyBytes)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toString().toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            serializedDocuments.forEach { (document, bytes) ->
                zip.putNextEntry(ZipEntry(DOCUMENT_PREFIX + encodedId(document.id) + ".json"))
                zip.write(bytes)
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
        val archiveFile = File(stagingDirectory, "incoming-backup.zip")
        try {
            var archiveBytes = 0L
            archiveFile.outputStream().buffered().use { output ->
                input.use { source ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        archiveBytes += count
                        require(archiveBytes <= MAX_ARCHIVE_BYTES) {
                            "Backup archive exceeds compressed size limit"
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }

            // ZipFile requires a valid central directory; this rejects archives
            // truncated after their local entries but before the ZIP directory.
            ZipFile(archiveFile).use { zip ->
                val iterator = zip.entries()
                while (iterator.hasMoreElements()) {
                    val entry = iterator.nextElement()
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
                    zip.getInputStream(entry).buffered().use { source ->
                        destination.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = source.read(buffer)
                                if (count < 0) break
                                entryBytes += count
                                totalBytes += count
                                require(entryBytes <= MAX_ENTRY_BYTES && totalBytes <= MAX_TOTAL_BYTES) {
                                    "Backup archive exceeds size limits"
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    entries[name] = destination
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
        id.toByteArray(StandardCharsets.UTF_8).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }

    private fun isUuid(value: String): Boolean =
        runCatching { java.util.UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)
}
