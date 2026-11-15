package com.magnumold.notes

import android.content.Context
import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.model.ParagraphBlock
import com.magnumold.notes.editor.model.RichText
import com.magnumold.notes.editor.storage.DocumentPersistenceController
import com.magnumold.notes.editor.storage.DocumentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DocumentPersistenceControllerTest {
    private fun repository(name: String): DocumentRepository {
        val context = RuntimeEnvironment.getApplication() as Context
        java.io.File(context.filesDir, name).deleteRecursively()
        return DocumentRepository(context, name)
    }

    private fun document(id: String, text: String) = EditorDocument(
        id = id,
        createdAt = 10L,
        updatedAt = 20L,
        blocks = listOf(ParagraphBlock(content = RichText(text)))
    )

    @Test
    fun opensStoredDocumentAndDebouncedChangesCanBeFlushed() = runBlocking {
        val repository = repository("persistence-controller-test")
        val original = document("doc-1", "Saved")
        repository.save(original)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = DocumentPersistenceController(repository, scope, debounceMillis = 60_000)
            assertEquals(original, controller.open(document("doc-1", "Initial")).document)

            val changed = document("doc-1", "Changed")
            controller.update(changed)
            assertEquals(true, controller.state.value.isDirty)

            controller.flush().getOrThrow()

            assertEquals(changed, repository.load("doc-1").getOrNull())
            assertFalse(controller.state.value.isDirty)
            assertNotNull(controller.state.value.lastSavedAt)
            assertNull(repository.loadRecoveryDraft().getOrNull())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun recoveryDraftIsOfferedAndOnlyRestoredWhenExplicitlyRequested() = runBlocking {
        val repository = repository("persistence-recovery-controller-test")
        val primary = document("doc-2", "Primary")
        val draft = document("doc-2", "Recovery draft")
        repository.save(primary)
        repository.saveRecoveryDraft(draft)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = DocumentPersistenceController(repository, scope, debounceMillis = 60_000)
            val opened = controller.open(document("doc-2", "Initial"))
            assertEquals(primary, opened.document)
            assertEquals(draft, opened.recoveryDraft)
            assertEquals(primary, repository.load("doc-2").getOrNull())

            controller.restoreRecoveryDraft()
            controller.flush().getOrThrow()

            assertEquals(draft, repository.load("doc-2").getOrNull())
            assertNull(repository.loadRecoveryDraft().getOrNull())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun recoveryDraftForAnotherDocumentIsNeverApplied() = runBlocking {
        val repository = repository("persistence-other-draft-test")
        val unrelated = document("other-doc", "Other")
        repository.saveRecoveryDraft(unrelated)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = DocumentPersistenceController(repository, scope, debounceMillis = 60_000)
            val opened = controller.open(document("doc-3", "Current"))
            assertEquals("Current", opened.document?.plainText())
            assertNull(opened.recoveryDraft)
            assertEquals(unrelated, repository.loadRecoveryDraft().getOrNull())
        } finally {
            scope.cancel()
        }
    }
}
