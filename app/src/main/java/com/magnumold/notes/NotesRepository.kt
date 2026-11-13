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
