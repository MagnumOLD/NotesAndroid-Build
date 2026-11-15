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
        return DocumentRepository(context.getSharedPreferences(name, Context.MODE_PRIVATE))
    }

    private fun document(id: String, text: String) = EditorDocument(
        id = id,
        createdAt = 10L,
        updatedAt = 20L,
        blocks = listOf(ParagraphBlock(content = RichText(text)))
    )

    @Test
    fun savesLoadsAndDeletesDocumentsWithoutAffectingOtherDocuments() {
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
    fun corruptDocumentIsReportedAndRawEntryIsNotDiscarded() {
        val context = RuntimeEnvironment.getApplication() as Context
        val preferences = context.getSharedPreferences("document-corrupt-test", Context.MODE_PRIVATE)
        val store = DocumentRepository(preferences)
        val valid = document("valid", "Keep me")
        store.save(valid)

        // Corrupt the payload while retaining the repository's index.
        preferences.edit().putString("editor_document_v1_dmFsaWQ", "{broken").commit()

        val snapshot = store.loadAll()
        assertEquals(listOf(valid), snapshot.documents)
        assertEquals(listOf("valid"), snapshot.corruptDocumentIds)
        assertEquals("{broken", preferences.getString("editor_document_v1_dmFsaWQ", null))
    }
}
