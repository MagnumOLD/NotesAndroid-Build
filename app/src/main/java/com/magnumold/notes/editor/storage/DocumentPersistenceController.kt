package com.magnumold.notes.editor.storage

import com.magnumold.notes.editor.model.EditorDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class DocumentPersistenceState(
    val document: EditorDocument? = null,
    val recoveryDraft: EditorDocument? = null,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val lastSavedAt: Long? = null,
    val storageError: String? = null
)

/**
 * Coordinates debounced autosave and crash recovery for one open document.
 *
 * A recovery copy is committed before the primary document. If the process is
 * interrupted between those writes, the next open can offer the draft without
 * silently replacing the last known-good document.
 */
internal class DocumentPersistenceController(
    private val repository: DocumentRepository,
    private val scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS
) {
    private val mutex = Mutex()
    private var pendingSave: Job? = null
    private val _state = MutableStateFlow(DocumentPersistenceState())
    val state: StateFlow<DocumentPersistenceState> = _state.asStateFlow()

    init {
        require(debounceMillis >= 0) { "Debounce must not be negative" }
    }

    suspend fun open(initialDocument: EditorDocument): DocumentPersistenceState = mutex.withLock {
        val loaded = withContext(Dispatchers.IO) { repository.load(initialDocument.id) }
        val current = loaded.getOrNull() ?: initialDocument
        val draftResult = withContext(Dispatchers.IO) { repository.loadRecoveryDraft() }
        val draft = draftResult.getOrNull()
            ?.takeIf { it.id == initialDocument.id && it != current }
        val errors = listOfNotNull(
            loaded.exceptionOrNull()?.message,
            draftResult.exceptionOrNull()?.message
        )
        DocumentPersistenceState(
            document = current,
            recoveryDraft = draft,
            storageError = errors.takeIf { it.isNotEmpty() }?.joinToString("; ")
        ).also { _state.value = it }
    }

    fun update(document: EditorDocument) {
        val current = _state.value.document
            ?: throw IllegalStateException("Open a document before editing")
        require(document.id == current.id) { "A persistence session cannot change document ids" }
        _state.update { it.copy(document = document, isDirty = true, storageError = null) }
        scheduleSave()
    }

    fun restoreRecoveryDraft() {
        val draft = _state.value.recoveryDraft ?: return
        update(draft)
    }

    suspend fun discardRecoveryDraft() {
        val draft = _state.value.recoveryDraft ?: return
        withContext(Dispatchers.IO) { repository.clearRecoveryDraft(draft.id) }
        _state.update { state ->
            if (state.recoveryDraft?.id == draft.id) state.copy(recoveryDraft = null) else state
        }
    }

    suspend fun flush(): Result<Unit> = runCatching {
        mutex.withLock {
            val document = _state.value.document ?: return@withLock
            if (!_state.value.isDirty) return@withLock
            _state.update { it.copy(isSaving = true, storageError = null) }
            try {
                withContext(Dispatchers.IO) {
                    repository.saveRecoveryDraft(document)
                    repository.save(document)
                }
                _state.update { current ->
                    if (current.document == document) {
                        current.copy(
                            isDirty = false,
                            isSaving = false,
                            recoveryDraft = null,
                            lastSavedAt = System.currentTimeMillis(),
                            storageError = null
                        )
                    } else {
                        current.copy(isSaving = false)
                    }
                }
            } catch (error: Throwable) {
                _state.update {
                    it.copy(isSaving = false, isDirty = true, storageError = error.message ?: "Не удалось сохранить документ")
                }
                throw error
            }
        }
    }

    suspend fun close() {
        pendingSave?.cancel()
        flush().getOrThrow()
    }

    private fun scheduleSave() {
        pendingSave?.cancel()
        pendingSave = scope.launch {
            delay(debounceMillis)
            flush()
        }
    }

    private companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 450L
    }
}
