package com.magnumold.notes

import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.model.ImageBlock
import com.magnumold.notes.editor.model.ParagraphBlock
import com.magnumold.notes.editor.model.RichText
import com.magnumold.notes.editor.storage.RichDocumentBackupArchive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RichDocumentBackupArchiveTest {
    @Test
    fun roundTripPreservesDocumentsAndReferencedAssets() {
        val root = createTempDir(prefix = "rich-backup-test")
        try {
            val asset = File(root, "source.asset").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
            val document = EditorDocument(
                id = "note-42",
                createdAt = 10L,
                updatedAt = 20L,
                blocks = listOf(
                    ParagraphBlock(content = RichText("Rich text")),
                    ImageBlock(assetId = "550e8400-e29b-41d4-a716-446655440000", caption = "Photo")
                )
            )
            val output = ByteArrayOutputStream()
            RichDocumentBackupArchive.write(
                output,
                """{"format":"notes_backup","version":7}""",
                listOf(document),
                mapOf("550e8400-e29b-41d4-a716-446655440000" to asset)
            )

            val payload = RichDocumentBackupArchive.read(
                ByteArrayInputStream(output.toByteArray()),
                File(root, "staging")
            )

            assertEquals("""{"format":"notes_backup","version":7}""", payload.legacySnapshotJson)
            assertEquals(listOf(document), payload.documents)
            assertEquals(1, payload.assets.size)
            assertEquals(asset.readBytes().toList(), payload.assets.single().file.readBytes().toList())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsArchiveTruncatedBeforeCentralDirectory() {
        val document = EditorDocument(
            id = "truncated-check",
            createdAt = 1L,
            updatedAt = 2L,
            blocks = listOf(ParagraphBlock(content = RichText("text")))
        )
        val output = ByteArrayOutputStream()
        RichDocumentBackupArchive.write(output, "{}", listOf(document), emptyMap())
        val complete = output.toByteArray()
        val truncated = complete.copyOf(complete.size - 8)
        val root = createTempDir(prefix = "rich-backup-truncated")
        try {
            assertThrows(Exception::class.java) {
                RichDocumentBackupArchive.read(ByteArrayInputStream(truncated), File(root, "staging"))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsMissingReferencedAssetBeforeWritingArchive() {
        val document = EditorDocument(
            id = "note-43",
            createdAt = 10L,
            updatedAt = 20L,
            blocks = listOf(ImageBlock(assetId = "550e8400-e29b-41d4-a716-446655440000"))
        )
        assertThrows(IllegalArgumentException::class.java) {
            RichDocumentBackupArchive.write(ByteArrayOutputStream(), "{}", listOf(document), emptyMap())
        }
    }

    @Test
    fun rejectsTruncatedOrNonZipInput() {
        val root = createTempDir(prefix = "rich-backup-invalid")
        try {
            assertThrows(Exception::class.java) {
                RichDocumentBackupArchive.read(ByteArrayInputStream("not a zip".toByteArray()), File(root, "staging"))
            }
        } finally {
            root.deleteRecursively()
        }
    }
    @Test
    fun rejectsUnsafeAssetIdsBeforeWritingZipEntries() {
        val root = createTempDir(prefix = "rich-backup-unsafe-id")
        try {
            val asset = File(root, "asset.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val document = EditorDocument(
                id = "unsafe-id-test",
                createdAt = 1L,
                updatedAt = 1L,
                blocks = listOf(ImageBlock(assetId = "../outside"))
            )
            assertThrows(IllegalArgumentException::class.java) {
                RichDocumentBackupArchive.write(
                    ByteArrayOutputStream(),
                    "{}",
                    listOf(document),
                    mapOf("../outside" to asset)
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

}
