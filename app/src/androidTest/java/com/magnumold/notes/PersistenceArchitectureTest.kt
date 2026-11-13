package com.magnumold.notes

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceArchitectureTest {

    private fun record(
        id: Long,
        type: RecordType,
        children: List<Note> = emptyList()
    ) = Note(
        id = id,
        title = "",
        blocks = listOf(NoteBlock("record $id", id = "block-$id")),
        updated = id,
        created = id,
        type = type,
        children = children
    )

    @Test
    fun backupRoundTripPreservesRecordsAndVersionSixSchema() {
        val childTask = record(2, RecordType.TASK)
        val task = record(1, RecordType.TASK, listOf(childTask))
        val purchase = record(3, RecordType.PURCHASE)
        val snapshot = PersistenceSnapshot(
            notes = listOf(task),
            collections = listOf(NoteCollection(10, "Избранное", listOf(purchase))),
            archived = listOf(VaultNote(record(4, RecordType.NOTE), 10, 1, 400)),
            trash = listOf(VaultNote(record(5, RecordType.NOTE), null, 0, 500))
        )

        val encoded = createBackupJson(
            snapshot.notes,
            snapshot.collections,
            snapshot.archived,
            snapshot.trash
        )
        val root = JSONObject(encoded)
        val decoded = parseBackupJson(encoded)

        assertEquals("notes_backup", root.getString("format"))
        assertEquals(6, root.getInt("version"))
        assertEquals(snapshot.notes, decoded.notes)
        assertEquals(snapshot.collections, decoded.collections)
        assertEquals(snapshot.archived, decoded.archived)
        assertEquals(snapshot.trash, decoded.trash)
    }

    @Test
    fun repositoryRoundTripPreservesAllFourPreferenceCollections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("notes_repository_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val snapshot = PersistenceSnapshot(
                notes = listOf(record(11, RecordType.NOTE)),
                collections = listOf(NoteCollection(12, "Работа", listOf(record(13, RecordType.TASK)))),
                archived = listOf(VaultNote(record(14, RecordType.NOTE), null, 2, 1400)),
                trash = listOf(VaultNote(record(15, RecordType.PURCHASE), 12, 0, 1500))
            )

            NotesRepository(preferences).saveSnapshot(snapshot)

            assertEquals(snapshot, NotesRepository(preferences).loadSnapshot())
            assertTrue(preferences.contains("notes"))
            assertTrue(preferences.contains("collections"))
            assertTrue(preferences.contains("archive"))
            assertTrue(preferences.contains("trash"))
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun legacyUniversalRecordsAreMigratedToTasks() {
        val legacyRecord = JSONObject()
            .put("id", 21)
            .put("type", "UNIVERSAL")
            .put("blocks", JSONArray().put(
                JSONObject()
                    .put("id", "legacy-block")
                    .put("text", "Старая запись")
                    .put("checked", false)
                    .put("checklist", false)
            ))
        val backup = JSONObject()
            .put("format", "notes_backup")
            .put("version", 5)
            .put("notes", JSONArray().put(legacyRecord))
            .toString()

        val decoded = parseBackupJson(backup)

        assertEquals(RecordType.TASK, decoded.notes.single().type)
        assertEquals("Старая запись", decoded.notes.single().blocks.single().text)
    }
}
