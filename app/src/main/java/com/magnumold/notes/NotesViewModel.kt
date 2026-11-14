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
    private val repository: NotesRepository,
    private val timeTrackingRepository: TimeTrackingRepository
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

    fun refreshFromRepository() {
        _snapshot.value = repository.loadSnapshot()
    }

    private fun updateSnapshot(transform: (PersistenceSnapshot) -> PersistenceSnapshot) {
        _snapshot.update(transform)
    }

    fun createCollection(id: Long, name: String) {
        updateSnapshot { CollectionOperations.create(it, id, name) }
    }

    fun renameCollection(id: Long, name: String) {
        updateSnapshot { CollectionOperations.rename(it, id, name) }
    }

    fun deleteCollection(id: Long): Boolean {
        if (_snapshot.value.collections.none { it.id == id }) return false
        updateSnapshot { CollectionOperations.delete(it, id) }
        return true
    }

    fun moveCollection(id: Long, delta: Int) {
        updateSnapshot { CollectionOperations.move(it, id, delta) }
    }

    fun transferRecords(sourceId: Long?, targetId: Long?, ids: Set<Long>) {
        updateSnapshot { CollectionOperations.transfer(it, sourceId, targetId, ids) }
    }

    fun createActiveRecord(collectionId: Long?, note: Note) {
        updateSnapshot { RecordListOperations.create(it, collectionId, note) }
    }

    fun updateActiveRecord(collectionId: Long?, note: Note) {
        updateSnapshot { RecordListOperations.update(it, collectionId, note) }
    }

    fun removeActiveRecord(collectionId: Long?, noteId: Long) {
        updateSnapshot { RecordListOperations.remove(it, collectionId, noteId) }
    }

    fun reorderActiveRecords(collectionId: Long?, ids: Set<Long>, afterId: Long?) {
        updateSnapshot { RecordListOperations.reorder(it, collectionId, ids, afterId) }
    }

    fun toggleActiveRecordsPinned(collectionId: Long?, ids: Set<Long>) {
        updateSnapshot { RecordListOperations.togglePinned(it, collectionId, ids) }
    }

    fun changeActiveRecordColor(collectionId: Long?, noteId: Long, colorIndex: Int) {
        updateSnapshot { RecordListOperations.changeColor(it, collectionId, noteId, colorIndex) }
    }

    fun importBackup(json: String): Int {
        val imported = parseBackupJson(json)
        imported.timeTracking?.let(timeTrackingRepository::restoreBackup)
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
    private val repository: NotesRepository,
    private val timeTrackingRepository: TimeTrackingRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(NotesViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return NotesViewModel(repository, timeTrackingRepository) as T
    }
}
