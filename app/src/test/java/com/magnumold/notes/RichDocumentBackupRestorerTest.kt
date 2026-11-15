package com.magnumold.notes

import android.content.Context
import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.model.ImageBlock
import com.magnumold.notes.editor.model.ParagraphBlock
import com.magnumold.notes.editor.model.RichText
import com.magnumold.notes.editor.model.plainText
import com.magnumold.notes.editor.storage.DocumentAssetStore
import com.magnumold.notes.editor.storage.DocumentRepository
import com.magnumold.notes.editor.storage.RichDocumentBackupArchive
import com.magnumold.notes.editor.storage.RichDocumentBackupRestorer
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RichDocumentBackupRestorerTest {
    private fun context() = RuntimeEnvironment.getApplication() as Context

    @Test
    fun restoresDocumentAndRemapsItsAssetReference() {
        val context = context()
        val docs = File(context.filesDir, "restore-docs-a").apply { deleteRecursively() }
        val assets = File(context.filesDir, "restore-assets-a").apply { deleteRecursively() }
        val repository = DocumentRepository(context, "restore-docs-a")
        val assetStore = DocumentAssetStore(context, directoryName = "restore-assets-a")
        val stagedFile = File(context.cacheDir, "restore-source-a").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        val oldAssetId = "550e8400-e29b-41d4-a716-446655440000"
        val document = EditorDocument(
            id = "backup-note",
            createdAt = 1L,
            updatedAt = 2L,
            blocks = listOf(ParagraphBlock(content = RichText("Text")), ImageBlock(assetId = oldAssetId))
        )
        try {
            val report = RichDocumentBackupRestorer(repository, assetStore).restore(
                RichDocumentBackupArchive.Payload(
                    legacySnapshotJson = "{}",
                    documents = listOf(document),
                    assets = listOf(RichDocumentBackupArchive.StagedAsset(oldAssetId, stagedFile))
                )
            )
            val restored = repository.load("backup-note").getOrNull()!!
            val newAssetId = (restored.blocks.last() as ImageBlock).assetId
            assertEquals(1, report.importedDocuments)
            assertNotEquals(oldAssetId, newAssetId)
            assertEquals(byteArrayOf(9, 8, 7).toList(), assetStore.find(newAssetId)!!.readBytes().toList())
            assertEquals(newAssetId, report.remappedAssetIds[oldAssetId])
        } finally {
            docs.deleteRecursively()
            assets.deleteRecursively()
            stagedFile.delete()
        }
    }

    @Test
    fun existingDocumentIsNeverOverwrittenOnIdCollision() {
        val context = context()
        val docs = File(context.filesDir, "restore-docs-b").apply { deleteRecursively() }
        val assets = File(context.filesDir, "restore-assets-b").apply { deleteRecursively() }
        val repository = DocumentRepository(context, "restore-docs-b")
        val assetStore = DocumentAssetStore(context, directoryName = "restore-assets-b")
        val existing = EditorDocument(
            id = "same-id",
            createdAt = 1L,
            updatedAt = 1L,
            blocks = listOf(ParagraphBlock(content = RichText("Keep me")))
        )
        repository.save(existing)
        val incoming = existing.copy(
            updatedAt = 2L,
            blocks = listOf(ParagraphBlock(content = RichText("Imported version")))
        )
        try {
            val report = RichDocumentBackupRestorer(repository, assetStore).restore(
                RichDocumentBackupArchive.Payload("{}", listOf(incoming), emptyList())
            )
            assertEquals(existing, repository.load("same-id").getOrNull())
            assertEquals(1, report.importedDocuments)
            assertNotEquals("same-id", report.remappedDocumentIds["same-id"])
            assertTrue(repository.load(report.remappedDocumentIds.getValue("same-id")).getOrNull() != null)
        } finally {
            docs.deleteRecursively()
            assets.deleteRecursively()
        }
    }
    @Test
    fun numericDocumentCollisionRemapsMatchingLegacyNoteId() {
        val context = context()
        val docs = File(context.filesDir, "restore-docs-c").apply { deleteRecursively() }
        val assets = File(context.filesDir, "restore-assets-c").apply { deleteRecursively() }
        val repository = DocumentRepository(context, "restore-docs-c")
        val assetStore = DocumentAssetStore(context, directoryName = "restore-assets-c")
        repository.save(
            EditorDocument(
                id = "17",
                createdAt = 1L,
                updatedAt = 1L,
                blocks = listOf(ParagraphBlock(content = RichText("Existing")))
            )
        )
        val incoming = EditorDocument(
            id = "17",
            createdAt = 2L,
            updatedAt = 2L,
            blocks = listOf(ParagraphBlock(content = RichText("Imported")))
        )
        try {
            val report = RichDocumentBackupRestorer(repository, assetStore).restore(
                RichDocumentBackupArchive.Payload("{}", listOf(incoming), emptyList()),
                reservedNoteIds = setOf(17L),
                incomingNoteIds = setOf(17L)
            )
            val remappedId = report.remappedDocumentIds.getValue("17").toLong()
            val legacy = BackupData(
                notes = listOf(Note(17L, "", listOf(NoteBlock("Imported")), 2L, created = 2L)),
                collections = emptyList(),
                archived = emptyList(),
                trash = emptyList()
            ).remapNoteIds(report.remappedDocumentIds)
            assertNotEquals(17L, remappedId)
            assertEquals(remappedId, legacy.notes.single().id)
            assertEquals("Imported", repository.load(remappedId.toString()).getOrNull()!!.plainText())
        } finally {
            docs.deleteRecursively()
            assets.deleteRecursively()
        }
    }

    @Test
    fun identicalImageDocumentIsImportedAgainWhenItsLocalAssetIsMissing() {
        val context = context()
        val docs = File(context.filesDir, "restore-docs-d").apply { deleteRecursively() }
        val assets = File(context.filesDir, "restore-assets-d").apply { deleteRecursively() }
        val repository = DocumentRepository(context, "restore-docs-d")
        val assetStore = DocumentAssetStore(context, directoryName = "restore-assets-d")
        val assetId = "550e8400-e29b-41d4-a716-446655440001"
        val document = EditorDocument(
            id = "22",
            createdAt = 1L,
            updatedAt = 1L,
            blocks = listOf(ImageBlock(assetId = assetId))
        )
        repository.save(document)
        val stagedFile = File(context.cacheDir, "restore-source-d").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        try {
            val report = RichDocumentBackupRestorer(repository, assetStore).restore(
                RichDocumentBackupArchive.Payload(
                    "{}",
                    listOf(document),
                    listOf(RichDocumentBackupArchive.StagedAsset(assetId, stagedFile))
                ),
                reservedNoteIds = setOf(22L),
                incomingNoteIds = setOf(22L)
            )
            val newId = report.remappedDocumentIds.getValue("22")
            val restored = repository.load(newId).getOrNull()!!
            val restoredAssetId = (restored.blocks.single() as ImageBlock).assetId
            assertEquals(document, repository.load("22").getOrNull())
            assertNotEquals(assetId, restoredAssetId)
            assertEquals(byteArrayOf(4, 5, 6).toList(), assetStore.find(restoredAssetId)!!.readBytes().toList())
        } finally {
            docs.deleteRecursively()
            assets.deleteRecursively()
            stagedFile.delete()
        }
    }

    @Test
    fun rollbackRemovesOnlyFilesInstalledByRestoreAndPreservesRecoveryDraft() {
        val context = context()
        val docs = File(context.filesDir, "restore-docs-e").apply { deleteRecursively() }
        val assets = File(context.filesDir, "restore-assets-e").apply { deleteRecursively() }
        val repository = DocumentRepository(context, "restore-docs-e")
        val assetStore = DocumentAssetStore(context, directoryName = "restore-assets-e")
        val stagedFile = File(context.cacheDir, "restore-source-e").apply { writeBytes(byteArrayOf(1, 3, 5, 7)) }
        val sourceAssetId = "550e8400-e29b-41d4-a716-446655440002"
        val document = EditorDocument(
            id = "draft-preserve",
            createdAt = 1L,
            updatedAt = 2L,
            blocks = listOf(ImageBlock(assetId = sourceAssetId))
        )
        val recoveryDraft = EditorDocument(
            id = "draft-preserve",
            createdAt = 1L,
            updatedAt = 3L,
            blocks = listOf(ParagraphBlock(content = RichText("Unsaved recovery text")))
        )
        repository.saveRecoveryDraft(recoveryDraft)
        val restorer = RichDocumentBackupRestorer(repository, assetStore)
        try {
            val report = restorer.restore(
                RichDocumentBackupArchive.Payload(
                    "{}",
                    listOf(document),
                    listOf(RichDocumentBackupArchive.StagedAsset(sourceAssetId, stagedFile))
                )
            )
            val imported = repository.load("draft-preserve").getOrNull()!!
            val installedAssetId = (imported.blocks.single() as ImageBlock).assetId
            assertTrue(assetStore.find(installedAssetId)!!.isFile)

            restorer.rollback(report)

            assertTrue(repository.load("draft-preserve").getOrNull() == null)
            assertTrue(assetStore.find(installedAssetId) == null)
            assertEquals(recoveryDraft, repository.loadRecoveryDraft().getOrNull())
        } finally {
            docs.deleteRecursively()
            assets.deleteRecursively()
            stagedFile.delete()
        }
    }

    @Test
    fun failedAssetInstallRollsBackEarlierAssetsBeforeSavingDocuments() {
        val context = context()
        val docs = File(context.filesDir, "restore-docs-f").apply { deleteRecursively() }
        val assets = File(context.filesDir, "restore-assets-f").apply { deleteRecursively() }
        val repository = DocumentRepository(context, "restore-docs-f")
        val assetStore = DocumentAssetStore(context, maxAssetBytes = 1L, directoryName = "restore-assets-f")
        val firstId = "550e8400-e29b-41d4-a716-446655440003"
        val secondId = "550e8400-e29b-41d4-a716-446655440004"
        val firstFile = File(context.cacheDir, "restore-first-f").apply { writeBytes(byteArrayOf(1)) }
        val secondFile = File(context.cacheDir, "restore-second-f").apply { writeBytes(byteArrayOf(2, 3)) }
        val document = EditorDocument(
            id = "atomic-restore",
            createdAt = 1L,
            updatedAt = 2L,
            blocks = listOf(ImageBlock(assetId = firstId), ImageBlock(assetId = secondId))
        )
        try {
            assertThrows(IllegalArgumentException::class.java) {
                RichDocumentBackupRestorer(repository, assetStore).restore(
                    RichDocumentBackupArchive.Payload(
                        "{}",
                        listOf(document),
                        listOf(
                            RichDocumentBackupArchive.StagedAsset(firstId, firstFile),
                            RichDocumentBackupArchive.StagedAsset(secondId, secondFile)
                        )
                    )
                )
            }

            assertTrue(repository.load("atomic-restore").getOrNull() == null)
            assertEquals(0, assets.listFiles().orEmpty().size)
        } finally {
            docs.deleteRecursively()
            assets.deleteRecursively()
            firstFile.delete()
            secondFile.delete()
        }
    }

}
