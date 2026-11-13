package com.magnumold.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the complete record state and its persistence lifecycle.
 * UI state (navigation, selection, dialogs and theme) remains in Compose.
 */
internal class NotesViewModel(
    private val repository: NotesRepository
) : ViewModel() {
    private val _snapshot = MutableStateFlow(repository.loadSnapshot())
    val snapshot: StateFlow<PersistenceSnapshot> = _snapshot.asStateFlow()

    init {
        viewModelScope.launch {
            _snapshot
                .debounce(PERSISTENCE_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { value ->
                    withContext(Dispatchers.IO) {
                        repository.saveSnapshot(value)
                    }
                }
        }
    }

    fun updateSnapshot(transform: (PersistenceSnapshot) -> PersistenceSnapshot) {
        _snapshot.update(transform)
    }

    fun importBackup(json: String): Int {
        val imported = parseBackupJson(json)
        var importedCount = 0
        updateSnapshot { current ->
            val merged = RecordOperations.mergeBackup(current, imported)
            importedCount = RecordOperations.recordCount(merged) - RecordOperations.recordCount(current)
            merged
        }
        return importedCount
    }

    fun restoreVaultEntry(entry: VaultNote, fromArchive: Boolean) {
        updateSnapshot { RecordOperations.restoreVaultEntry(it, entry, fromArchive) }
    }

    fun moveArchivedToTrash(entry: VaultNote) {
        updateSnapshot { RecordOperations.moveArchivedToTrash(it, entry) }
    }

    fun deleteFromTrash(entry: VaultNote) {
        updateSnapshot { RecordOperations.deleteFromTrash(it, entry) }
    }

    fun emptyTrash() {
        updateSnapshot { it.copy(trash = emptyList()) }
    }

    fun moveActiveRecordsToVault(collectionId: Long?, ids: Set<Long>, toTrash: Boolean) {
        updateSnapshot { RecordOperations.moveActiveRecordsToVault(it, collectionId, ids, toTrash) }
    }

    private companion object {
        const val PERSISTENCE_DEBOUNCE_MS = 300L
    }
}

internal class NotesViewModelFactory(
    private val repository: NotesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(NotesViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return NotesViewModel(repository) as T
    }
}
