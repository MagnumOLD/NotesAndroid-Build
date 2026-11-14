package com.magnumold.notes

import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Single persistence boundary for local records.
 * Preference names and JSON keys are preserved for upgrade compatibility.
 */
internal class NotesRepository(private val preferences: SharedPreferences) {
    fun loadSnapshot() = PersistenceSnapshot(
        notes = loadNotes(),
        collections = loadCollections(),
        archived = loadVault("archive"),
        trash = loadVault("trash")
    )

    fun saveSnapshot(snapshot: PersistenceSnapshot) {
        // A single editor transaction keeps the four record collections consistent.
        preferences.edit()
            .putString("notes", notesToJson(snapshot.notes).toString())
            .putString("collections", collectionsToJson(snapshot.collections).toString())
            .putString("archive", vaultToJson(snapshot.archived).toString())
            .putString("trash", vaultToJson(snapshot.trash).toString())
            .apply()
    }

    fun updateTaskStatus(taskId: Long, status: RecordStatus): Boolean {
        val current = loadSnapshot()
        var found = false
        fun updateTree(items: List<Note>): List<Note> = items.map { note ->
            val updatedChildren = updateTree(note.children)
            if (note.id == taskId && note.type == RecordType.TASK) {
                found = true
                note.copy(status = status, deadlineResumeOverride = false, children = updatedChildren)
            } else if (updatedChildren != note.children) {
                note.copy(children = updatedChildren)
            } else note
        }
        val updatedNotes = updateTree(current.notes)
        val updatedCollections = current.collections.map { collection ->
            collection.copy(notes = updateTree(collection.notes))
        }
        if (found) {
            saveSnapshot(current.copy(notes = updatedNotes, collections = updatedCollections))
            preferences.edit().putLong(KEY_EXTERNAL_STATUS_UPDATE, System.currentTimeMillis()).apply()
        }
        return found
    }

    private companion object {
        const val KEY_EXTERNAL_STATUS_UPDATE = "external_task_status_update"
    }

    private fun loadNotes(): List<Note> = runCatching {
        parseNotesArray(JSONArray(preferences.getString("notes", "[]") ?: "[]"))
    }.getOrDefault(emptyList())

    private fun loadCollections(): List<NoteCollection> = runCatching {
        parseCollectionsArray(JSONArray(preferences.getString("collections", "[]") ?: "[]"))
    }.getOrDefault(emptyList())

    private fun loadVault(key: String): List<VaultNote> = runCatching {
        parseVaultArray(JSONArray(preferences.getString(key, "[]") ?: "[]"))
    }.getOrDefault(emptyList())
}
