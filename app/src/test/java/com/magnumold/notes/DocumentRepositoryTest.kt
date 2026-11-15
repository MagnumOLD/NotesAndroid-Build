package com.magnumold.notes

import android.content.Context
import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.model.ParagraphBlock
import com.magnumold.notes.editor.model.RichText
import com.magnumold.notes.editor.storage.DocumentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DocumentRepositoryTest {
    private fun repository(name: String): DocumentRepository {
        val context = RuntimeEnvironment.getApplication() as Context
        context.deleteDirForTest(name)
        return DocumentRepository(context, name)
    }

    private fun document(id: String, text: String) = EditorDocument(
        id = id,
        createdAt = 10L,
        updatedAt = 20L,
        blocks = listOf(ParagraphBlock(content = RichText(text)))
    )

    @Test
    fun savesLoadsListsAndDeletesDocumentsIndependently() {
        val store = repository("document-store-test")
        val first = document("note/one", "First")
        val second = document("note two", "Second")

        store.save(first)
        store.save(second)

        assertEquals(first, store.load(first.id).getOrNull())
        assertEquals(listOf(first, second), store.loadAll().documents)
        assertTrue(store.loadAll().corruptDocumentIds.isEmpty())

        store.delete(first.id)

        assertNull(store.load(first.id).getOrNull())
        assertEquals(listOf(second), store.loadAll().documents)
    }

    @Test
    fun recoveryDraftCanBeSavedRestoredAndCleared() {
        val store = repository("document-recovery-test")
        val draft = document("draft-1", "Unsaved work")

        store.saveRecoveryDraft(draft)
        assertEquals(draft, store.loadRecoveryDraft().getOrNull())

        store.clearRecoveryDraft()
        assertNull(store.loadRecoveryDraft().getOrNull())
    }

    @Test
    fun primarySaveClearsOnlyMatchingRecoveryDraft() {
        val store = repository("document-draft-safety-test")
        val draft = document("draft-2", "Unsaved")
        store.saveRecoveryDraft(draft)

        store.save(draft)

        assertNull(store.loadRecoveryDraft().getOrNull())
        assertEquals(draft, store.load(draft.id).getOrNull())
    }

    @Test
    fun corruptDocumentIsReportedAndRawEntryIsNotDiscarded() {
        val store = repository("document-corrupt-test")
        val valid = document("valid", "Keep me")
        store.save(valid)

        store.documentFileForTest("valid").writeText("{broken")

        val snapshot = store.loadAll()
        assertTrue(snapshot.documents.isEmpty())
        assertEquals(listOf("valid"), snapshot.corruptDocumentIds)
        assertEquals("{broken", store.documentFileForTest("valid").readText())
    }

    @Test
    fun documentIdsCannotEscapeTheStorageDirectory() {
        val store = repository("document-path-test")
        val unusual = document("../../outside/file", "Safe")
        store.save(unusual)

        assertEquals(unusual, store.load(unusual.id).getOrNull())
        assertEquals(1, store.loadAll().documents.size)
    }

    private fun Context.deleteDirForTest(name: String) {
        java.io.File(filesDir, name).deleteRecursively()
    }
}
