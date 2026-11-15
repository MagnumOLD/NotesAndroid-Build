package com.magnumold.notes

import org.json.JSONArray
import org.json.JSONObject

internal data class BackupData(val notes: List<Note>, val collections: List<NoteCollection>, val archived: List<VaultNote>, val trash: List<VaultNote>, val timeTracking: TimeTrackingState? = null)
private fun noteToJson(note: Note): JSONObject {
    val blocks = JSONArray()
    note.blocks.forEach { block ->
        blocks.put(JSONObject().put("id", block.id).put("text", block.text)
            .put("checked", block.checked).put("checklist", block.isChecklist))
    }
    return JSONObject()
        .put("id", note.id).put("title", note.title).put("updated", note.updated)
        .put("created", note.created).put("colorIndex", note.colorIndex).put("pinned", note.pinned)
        .put("type", note.type.name).put("status", note.status.name).put("timeTrackingEnabled", note.timeTrackingEnabled)
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
        created = created, type = type, status = status,
        timeTrackingEnabled = obj.optBoolean("timeTrackingEnabled", false), dateMode = dateMode,
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

internal fun createBackupJson(items: List<Note>, collections: List<NoteCollection>, archived: List<VaultNote>, trash: List<VaultNote>, timeTracking: TimeTrackingState = TimeTrackingState()): String {
    val collectionArray = JSONArray()
    collections.forEach { collection ->
        collectionArray.put(JSONObject().put("id", collection.id).put("name", collection.name).put("notes", notesToJson(collection.notes)))
    }
    return JSONObject().put("format", "notes_backup").put("version", 7).put("exportedAt", System.currentTimeMillis())
        .put("notes", notesToJson(items)).put("collections", collectionArray)
        .put("archive", vaultToJson(archived)).put("trash", vaultToJson(trash))
        .put("timeTracking", timeTrackingToJson(timeTracking)).toString(2)
}
internal fun parseBackupJson(raw: String): BackupData {
    val root = JSONObject(raw)
    require(root.optString("format") == "notes_backup") { "Это не резервная копия блокнота" }
    require(root.optInt("version") in 1..7) { "Неподдерживаемая версия файла" }
    val notes = parseNotesArray(root.optJSONArray("notes") ?: error("В файле нет списка заметок"))
    val collections = parseCollectionsArray(root.optJSONArray("collections") ?: JSONArray())
    return BackupData(notes, collections, parseVaultArray(root.optJSONArray("archive")), parseVaultArray(root.optJSONArray("trash")), root.optJSONObject("timeTracking")?.let(::parseTimeTrackingJson))
}

private fun Note.sameRecordContent(other: Note): Boolean =
    type == other.type && blocks.toPlainText() == other.blocks.toPlainText() &&
        titleText == other.titleText && descriptionText == other.descriptionText

private fun Note.allRecordIds(): Sequence<Long> = sequence {
    yield(id)
    children.forEach { child -> yieldAll(child.allRecordIds()) }
}

private fun stableImportedRecordId(note: Note, salt: Int): Long {
    val content = buildString {
        append(note.id).append('|').append(note.type.name).append('|')
        append(note.titleText).append('|').append(note.descriptionText).append('|')
        note.blocks.forEach { block ->
            append(block.text).append('|').append(block.checked).append('|')
                .append(block.isChecklist).append(';')
        }
        append('|').append(salt)
    }
    val candidate = java.util.UUID.nameUUIDFromBytes(content.toByteArray(Charsets.UTF_8))
        .mostSignificantBits and Long.MAX_VALUE
    return candidate.takeIf { it != 0L } ?: 1L
}

private fun mergeNoteLists(
    existing: List<Note>,
    imported: List<Note>,
    knownRecords: MutableMap<Long, Note>,
    taskIdRemaps: MutableMap<Long, Long>
): List<Note> {
    val result = existing.toMutableList()
    imported.forEach { incoming ->
        val sameIndex = result.indexOfFirst {
            it.id == incoming.id && it.sameRecordContent(incoming)
        }
        if (sameIndex >= 0) {
            val current = result[sameIndex]
            val mergedChildren = mergeNoteLists(current.children, incoming.children, knownRecords, taskIdRemaps)
            val updated = current.copy(children = mergedChildren)
            result[sameIndex] = updated
            knownRecords[updated.id] = updated
            return@forEach
        }

        val existingWithId = knownRecords[incoming.id]
        if (existingWithId != null && existingWithId.sameRecordContent(incoming)) {
            // The same logical record is already present in another section.
            return@forEach
        }

        val safeId = if (existingWithId == null) {
            incoming.id
        } else {
            var salt = 0
            var candidate: Long
            while (true) {
                candidate = stableImportedRecordId(incoming, salt++)
                val occupant = knownRecords[candidate]
                if (occupant == null || occupant.sameRecordContent(incoming)) break
            }
            if (knownRecords[candidate]?.sameRecordContent(incoming) == true) {
                return@forEach
            }
            candidate
        }

        val safeBlocks = if (safeId == incoming.id) incoming.blocks else incoming.blocks.map {
            it.copy(id = java.util.UUID.randomUUID().toString())
        }
        val safeChildren = mergeNoteLists(emptyList(), incoming.children, knownRecords, taskIdRemaps)
        val safeNote = incoming.copy(id = safeId, blocks = safeBlocks, children = safeChildren)
        if (incoming.type == RecordType.TASK && safeId != incoming.id) {
            taskIdRemaps.putIfAbsent(incoming.id, safeId)
        }
        result.add(safeNote)
        knownRecords[safeId] = safeNote
    }
    return result
}

private fun mergeVaultEntries(
    existing: List<VaultNote>,
    imported: List<VaultNote>,
    knownRecords: MutableMap<Long, Note>,
    taskIdRemaps: MutableMap<Long, Long>
): List<VaultNote> {
    val result = existing.toMutableList()
    imported.forEach { incoming ->
        val localIndex = result.indexOfFirst {
            it.note.id == incoming.note.id && it.note.sameRecordContent(incoming.note)
        }
        if (localIndex >= 0) {
            val current = result[localIndex]
            result[localIndex] = current.copy(
                note = current.note.copy(
                    children = mergeNoteLists(current.note.children, incoming.note.children, knownRecords, taskIdRemaps)
                )
            )
            return@forEach
        }
        val merged = mergeNoteLists(emptyList(), listOf(incoming.note), knownRecords, taskIdRemaps).singleOrNull()
        if (merged != null && result.none { it.note.id == merged.id }) {
            result.add(incoming.copy(note = merged))
        }
    }
    return result
}

private fun mergeCollectionLists(
    existing: List<NoteCollection>,
    imported: List<NoteCollection>,
    knownRecords: MutableMap<Long, Note>,
    taskIdRemaps: MutableMap<Long, Long>
): List<NoteCollection> {
    val result = existing.toMutableList()
    imported.forEach { incoming ->
        val sameName = result.indexOfFirst { it.name.equals(incoming.name, true) }
        if (sameName >= 0) {
            result[sameName] = result[sameName].copy(
                notes = mergeNoteLists(result[sameName].notes, incoming.notes, knownRecords, taskIdRemaps)
            )
        } else {
            var id = incoming.id
            val usedCollectionIds = result.mapTo(mutableSetOf()) { it.id }
            while (id in usedCollectionIds) id++
            result.add(incoming.copy(id = id, notes = mergeNoteLists(emptyList(), incoming.notes, knownRecords, taskIdRemaps)))
        }
    }
    return result
}

internal data class BackupMergeResult(
    val snapshot: PersistenceSnapshot,
    val taskIdRemaps: Map<Long, Long>
)

/** Merges every backup section against one shared namespace of record IDs. */
internal fun mergeBackupRecords(current: PersistenceSnapshot, imported: BackupData): BackupMergeResult {
    val knownRecords = mutableMapOf<Long, Note>()
    val taskIdRemaps = mutableMapOf<Long, Long>()
    fun remember(note: Note) {
        knownRecords.putIfAbsent(note.id, note)
        note.children.forEach(::remember)
    }
    current.notes.forEach(::remember)
    current.collections.forEach { it.notes.forEach(::remember) }
    current.archived.forEach { remember(it.note) }
    current.trash.forEach { remember(it.note) }

    val merged = current.copy(
        notes = mergeNoteLists(current.notes, imported.notes, knownRecords, taskIdRemaps),
        collections = mergeCollectionLists(current.collections, imported.collections, knownRecords, taskIdRemaps),
        archived = mergeVaultEntries(current.archived, imported.archived, knownRecords, taskIdRemaps),
        trash = mergeVaultEntries(current.trash, imported.trash, knownRecords, taskIdRemaps)
    )
    return BackupMergeResult(merged, taskIdRemaps)
}


/** Applies rich-document ID remaps to the corresponding legacy note records. */
internal fun BackupData.remapNoteIds(mapping: Map<String, String>): BackupData {
    if (mapping.isEmpty()) return this
    fun remap(note: Note): Note {
        val newId = mapping[note.id.toString()]?.toLongOrNull() ?: note.id
        return note.copy(id = newId, children = note.children.map(::remap))
    }
    return copy(
        notes = notes.map(::remap),
        collections = collections.map { it.copy(notes = it.notes.map(::remap)) },
        archived = archived.map { it.copy(note = remap(it.note)) },
        trash = trash.map { it.copy(note = remap(it.note)) }
    )
}
