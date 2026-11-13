package com.magnumold.notes

import org.json.JSONArray
import org.json.JSONObject

internal data class BackupData(val notes: List<Note>, val collections: List<NoteCollection>, val archived: List<VaultNote>, val trash: List<VaultNote>, val timeSessions: List<TaskTimeSession> = emptyList(), val timerPreferences: TimerPreferences? = null)
private fun noteToJson(note: Note): JSONObject {
    val blocks = JSONArray()
    note.blocks.forEach { block ->
        blocks.put(JSONObject().put("id", block.id).put("text", block.text)
            .put("checked", block.checked).put("checklist", block.isChecklist))
    }
    return JSONObject()
        .put("id", note.id).put("title", note.title).put("updated", note.updated)
        .put("created", note.created).put("colorIndex", note.colorIndex).put("pinned", note.pinned)
        .put("type", note.type.name).put("status", note.status.name)
        .put("dateMode", note.dateMode?.name ?: JSONObject.NULL)
        .put("dateAt", note.dateAt ?: JSONObject.NULL)
        .put("titleEnabled", note.titleEnabled).put("titleText", note.titleText)
        .put("descriptionEnabled", note.descriptionEnabled).put("descriptionText", note.descriptionText)
        .put("deadlineResumeOverride", note.deadlineResumeOverride)
        .put("statusBeforeAutoCompletion", note.statusBeforeAutoCompletion?.name ?: JSONObject.NULL)
        .put("blocks", blocks).put("children", notesToJson(note.children))
}
internal fun notesToJson(items: List<Note>): JSONArray {
    val array = JSONArray()
    items.filter { it.hasContent() }.forEach { array.put(noteToJson(it)) }
    return array
}
internal fun vaultToJson(items: List<VaultNote>): JSONArray {
    val array = JSONArray()
    items.forEach { entry ->
        array.put(JSONObject().put("collectionId", entry.collectionId ?: JSONObject.NULL)
            .put("position", entry.position).put("movedAt", entry.movedAt).put("note", noteToJson(entry.note)))
    }
    return array
}
private fun parseNoteObject(obj: JSONObject, index: Int): Note {
    val blocksJson = obj.optJSONArray("blocks") ?: JSONArray()
    var blocks = (0 until blocksJson.length()).map { blockIndex ->
        val block = blocksJson.getJSONObject(blockIndex)
        NoteBlock(block.optString("text"), block.optBoolean("checked"), block.optBoolean("checklist"),
            block.optString("id").ifBlank { java.util.UUID.randomUUID().toString() })
    }.ifEmpty { listOf(NoteBlock()) }
    val oldTitle = obj.optString("title").trim()
    if (oldTitle.isNotEmpty() && blocks.firstOrNull()?.text?.trim() != oldTitle) {
        blocks = listOf(NoteBlock(oldTitle)) + blocks
    }
    val storedType = obj.optString("type", "NOTE")
    // Legacy UNIVERSAL records are migrated to the new TASK container model.
    val type = if (storedType == "UNIVERSAL") RecordType.TASK
        else runCatching { RecordType.valueOf(storedType) }.getOrDefault(RecordType.NOTE)
    val status = runCatching { RecordStatus.valueOf(obj.optString("status", "WAITING")) }.getOrDefault(RecordStatus.WAITING)
    val dateMode = runCatching { RecordDateMode.valueOf(obj.optString("dateMode")) }.getOrNull()
    val children = parseNotesArray(obj.optJSONArray("children") ?: JSONArray())
    val updated = obj.optLong("updated", System.currentTimeMillis())
    val created = obj.optLong("created",
        obj.optLong("id", updated).takeIf { it > 1_000_000_000_000L } ?: updated)
    return Note(
        id = obj.optLong("id", index.toLong() + 1L), title = "", blocks = blocks, updated = updated,
        colorIndex = obj.optInt("colorIndex", 0).coerceIn(0, 6), pinned = obj.optBoolean("pinned", false),
        created = created, type = type, status = status, dateMode = dateMode,
        dateAt = if (obj.isNull("dateAt")) null else obj.optLong("dateAt"),
        titleEnabled = obj.optBoolean("titleEnabled", false), titleText = obj.optString("titleText"),
        descriptionEnabled = obj.optBoolean("descriptionEnabled", false),
        descriptionText = obj.optString("descriptionText"),
        deadlineResumeOverride = obj.optBoolean("deadlineResumeOverride", false), children = children,
        statusBeforeAutoCompletion = obj.optString("statusBeforeAutoCompletion").takeIf { it.isNotBlank() && it != "null" }
            ?.let { runCatching { RecordStatus.valueOf(it) }.getOrNull() }
    )
}
internal fun parseNotesArray(array: JSONArray): List<Note> =
    (0 until array.length()).map { index -> parseNoteObject(array.getJSONObject(index), index) }
        .filter { it.hasContent() }
internal fun parseVaultArray(array: JSONArray?): List<VaultNote> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val obj = array.optJSONObject(index) ?: return@mapNotNull null
        val noteObj = obj.optJSONObject("note") ?: return@mapNotNull null
        val note = parseNoteObject(noteObj, index).takeIf { it.hasContent() } ?: return@mapNotNull null
        val collectionId = if (obj.isNull("collectionId")) null else obj.optLong("collectionId")
        VaultNote(note, collectionId, obj.optInt("position", 0), obj.optLong("movedAt", System.currentTimeMillis()))
    }
}
internal fun collectionsToJson(items: List<NoteCollection>): JSONArray {
    val array = JSONArray()
    items.forEach { item ->
        array.put(JSONObject().put("id", item.id).put("name", item.name)
            .put("notes", notesToJson(item.notes)))
    }
    return array
}

internal fun parseCollectionsArray(array: JSONArray): List<NoteCollection> =
    (0 until array.length()).map { index ->
        val obj = array.getJSONObject(index)
        NoteCollection(
            obj.optLong("id", System.currentTimeMillis() + index),
            obj.optString("name").ifBlank { "Подборка ${index + 1}" },
            parseNotesArray(obj.optJSONArray("notes") ?: JSONArray())
        )
    }

internal fun createBackupJson(items: List<Note>, collections: List<NoteCollection>, archived: List<VaultNote>, trash: List<VaultNote>, timeSessions: List<TaskTimeSession> = emptyList(), timerPreferences: TimerPreferences = TimerPreferences()): String {
    val collectionArray = JSONArray()
    collections.forEach { collection ->
        collectionArray.put(JSONObject().put("id", collection.id).put("name", collection.name).put("notes", notesToJson(collection.notes)))
    }
    val sessionArray = JSONArray()
    timeSessions.forEach { session ->
        sessionArray.put(JSONObject().put("id", session.id).put("taskId", session.taskId)
            .put("startedAt", session.startedAtMillis).put("endedAt", session.endedAtMillis)
            .put("duration", session.durationMillis))
    }
    val normalizedTimerSettings = timerPreferences.normalized()
    val timerJson = JSONObject()
        .put("sessions", sessionArray)
        .put("defaultLimitMinutes", normalizedTimerSettings.defaultLimitMinutes)
        .put("endMode", normalizedTimerSettings.endMode.name)
        .put("breakMinutes", normalizedTimerSettings.breakMinutes)
        .put("soundEnabled", normalizedTimerSettings.soundEnabled)
    return JSONObject().put("format", "notes_backup").put("version", 7).put("exportedAt", System.currentTimeMillis())
        .put("notes", notesToJson(items)).put("collections", collectionArray)
        .put("archive", vaultToJson(archived)).put("trash", vaultToJson(trash))
        .put("timeTracking", timerJson).toString(2)
}
internal fun parseBackupJson(raw: String): BackupData {
    val root = JSONObject(raw)
    require(root.optString("format") == "notes_backup") { "Это не резервная копия блокнота" }
    require(root.optInt("version") in 1..7) { "Неподдерживаемая версия файла" }
    val notes = parseNotesArray(root.optJSONArray("notes") ?: error("В файле нет списка заметок"))
    val collections = parseCollectionsArray(root.optJSONArray("collections") ?: JSONArray())
    val timeTracking = root.optJSONObject("timeTracking")
    val sessionsJson = timeTracking?.optJSONArray("sessions") ?: JSONArray()
    val sessions = (0 until sessionsJson.length()).mapNotNull { index ->
        val item = sessionsJson.optJSONObject(index) ?: return@mapNotNull null
        TaskTimeSession(
            id = item.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            taskId = item.optLong("taskId"),
            startedAtMillis = item.optLong("startedAt"),
            endedAtMillis = item.optLong("endedAt"),
            durationMillis = item.optLong("duration").coerceAtLeast(0L)
        )
    }
    val timerPreferences = timeTracking?.let { timer ->
        val endMode = runCatching { TimerEndMode.valueOf(timer.optString("endMode", "SIGNAL_ONLY")) }
            .getOrDefault(TimerEndMode.SIGNAL_ONLY)
        TimerPreferences(
            defaultLimitMinutes = timer.optInt("defaultLimitMinutes", 25),
            endMode = endMode,
            breakMinutes = timer.optInt("breakMinutes", 5),
            soundEnabled = timer.optBoolean("soundEnabled", true)
        ).normalized()
    }
    return BackupData(notes, collections, parseVaultArray(root.optJSONArray("archive")),
        parseVaultArray(root.optJSONArray("trash")), sessions, timerPreferences)
}

private fun Note.sameRecordContent(other: Note): Boolean =
    type == other.type && blocks.toPlainText() == other.blocks.toPlainText() &&
        titleText == other.titleText && descriptionText == other.descriptionText

internal fun mergeImportedNotes(existing: List<Note>, imported: List<Note>): List<Note> {
    val result = existing.toMutableList()
    imported.forEach { incoming ->
        val sameIndex = result.indexOfFirst { it.sameRecordContent(incoming) }
        if (sameIndex >= 0) {
            val current = result[sameIndex]
            result[sameIndex] = current.copy(
                children = mergeImportedNotes(current.children, incoming.children)
            )
            return@forEach
        }
        val collision = result.any { it.id == incoming.id }
        val safeId = if (!collision) incoming.id else {
            var candidate = System.currentTimeMillis()
            while (result.any { it.id == candidate }) candidate++
            candidate
        }
        val blocks = if (collision) incoming.blocks.map {
            it.copy(id = java.util.UUID.randomUUID().toString())
        } else incoming.blocks
        result.add(incoming.copy(id = safeId, blocks = blocks))
    }
    return result
}

internal fun mergeImportedVault(existing: List<VaultNote>, imported: List<VaultNote>): List<VaultNote> {
    val result = existing.toMutableList()
    imported.forEach { incoming ->
        val sameIndex = result.indexOfFirst { it.note.sameRecordContent(incoming.note) }
        if (sameIndex >= 0) {
            val current = result[sameIndex]
            result[sameIndex] = current.copy(
                note = current.note.copy(
                    children = mergeImportedNotes(current.note.children, incoming.note.children)
                )
            )
            return@forEach
        }
        val collision = result.any { it.note.id == incoming.note.id }
        val safeNote = if (!collision) incoming.note else {
            var candidate = System.currentTimeMillis()
            while (result.any { it.note.id == candidate }) candidate++
            incoming.note.copy(id = candidate, blocks = incoming.note.blocks.map { it.copy(id = java.util.UUID.randomUUID().toString()) })
        }
        result.add(incoming.copy(note = safeNote))
    }
    return result
}

internal fun mergeImportedCollections(existing: List<NoteCollection>, imported: List<NoteCollection>): List<NoteCollection> {
    val result = existing.toMutableList()
    imported.forEach { incoming ->
        val sameName = result.indexOfFirst { it.name.equals(incoming.name, true) }
        if (sameName >= 0) result[sameName] = result[sameName].copy(notes = mergeImportedNotes(result[sameName].notes, incoming.notes))
        else {
            var id = incoming.id
            while (result.any { it.id == id }) id++
            result.add(incoming.copy(id = id))
        }
    }
    return result
}
