package com.magnumold.notes

import android.content.Context
import com.magnumold.notes.editor.storage.DocumentAssetStore
import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DocumentAssetStoreTest {
    private fun store(name: String, maxBytes: Long = 1024L): DocumentAssetStore {
        val context = RuntimeEnvironment.getApplication() as Context
        return DocumentAssetStore(context, maxBytes)
    }

    @Test
    fun importsAssetIntoPrivateStorageAndCanReadItBack() {
        val bytes = byteArrayOf(0, 1, 2, 3, 4, 5)
        val asset = store("unused").import(ByteArrayInputStream(bytes), "photo.png", "image/png")

        assertTrue(asset.file.isFile)
        assertEquals("photo.png", asset.fileName)
        assertEquals("image/png", asset.mimeType)
        assertEquals(bytes.size.toLong(), asset.sizeBytes)
        assertArrayEquals(bytes, store("unused").open(asset.assetId).use { it.readBytes() })
    }

    @Test
    fun pruneKeepsReferencedAssetsAndRemovesOrphans() {
        val store = store("unused")
        val kept = store.import(ByteArrayInputStream(byteArrayOf(1)), "kept.bin")
        val orphan = store.import(ByteArrayInputStream(byteArrayOf(2)), "orphan.bin")

        assertEquals(1, store.prune(setOf(kept.assetId)))
        assertNotNull(store.find(kept.assetId))
        assertNull(store.find(orphan.assetId))
    }

    @Test
    fun rejectsOversizedAssetAndRemovesPartialFile() {
        val store = store("unused", maxBytes = 3)
        assertThrows(IllegalArgumentException::class.java) {
            store.import(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), "large.bin")
        }
        assertEquals(0, store.prune(emptySet()))
    }

    @Test
    fun rejectsPathTraversalAndUnknownAssetIds() {
        val store = store("unused")
        assertNull(store.find("../../outside"))
        assertFalse(store.delete("../../outside"))
        assertThrows(IllegalArgumentException::class.java) { store.open("../../outside") }
    }
}
