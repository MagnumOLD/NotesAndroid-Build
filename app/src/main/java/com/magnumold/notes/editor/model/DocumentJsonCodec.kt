package com.magnumold.notes.editor.model

import org.json.JSONArray
import org.json.JSONObject

internal object DocumentJsonCodec {
    fun encode(document: EditorDocument): String = documentToJson(document).toString()

    fun decode(raw: String): EditorDocument = documentFromJson(JSONObject(raw))

    private fun documentToJson(document: EditorDocument) = JSONObject()
        .put("id", document.id)
        .put("schemaVersion", document.schemaVersion)
        .put("createdAt", document.createdAt)
        .put("updatedAt", document.updatedAt)
        .put("metadata", JSONObject()
            .put("title", document.metadata.title)
            .put("tags", JSONArray(document.metadata.tags)))
        .put("blocks", JSONArray().apply { document.blocks.forEach { put(blockToJson(it)) } })

    private fun blockToJson(block: DocumentBlock): JSONObject {
        val result = JSONObject().put("id", block.id)
        when (block) {
            is ParagraphBlock -> result.put("type", "paragraph").put("content", richTextToJson(block.content))
            is HeadingBlock -> result.put("type", "heading").put("level", block.level).put("content", richTextToJson(block.content))
            is ListBlock -> result.put("type", "list").put("ordered", block.ordered).put("items", listItemsToJson(block.items))
            is ChecklistBlock -> result.put("type", "checklist").put("items", checklistItemsToJson(block.items))
            is QuoteBlock -> result.put("type", "quote").put("content", richTextToJson(block.content))
            is CodeBlock -> result.put("type", "code").put("code", block.code).put("language", block.language ?: JSONObject.NULL)
            is TableBlock -> result.put("type", "table").put("rows", JSONArray().apply {
                block.rows.forEach { row -> put(JSONObject().put("id", row.id).put("cells", JSONArray().apply {
                    row.cells.forEach { put(richTextToJson(it.content)) }
                })) }
            })
            is ImageBlock -> result.put("type", "image").put("assetId", block.assetId)
                .put("widthFraction", block.widthFraction.toDouble()).put("alignment", block.alignment.name)
                .put("caption", block.caption)
            is AttachmentBlock -> result.put("type", "attachment").put("assetId", block.assetId)
                .put("fileName", block.fileName).put("mimeType", block.mimeType ?: JSONObject.NULL)
                .put("sizeBytes", block.sizeBytes ?: JSONObject.NULL)
            is LinkBlock -> result.put("type", "link").put("label", block.label).put("url", block.url)
            is CalloutBlock -> result.put("type", "callout").put("kind", block.kind.name)
                .put("title", block.title).put("content", richTextToJson(block.content))
            is DividerBlock -> result.put("type", "divider")
        }
        return result
    }

    private fun richTextToJson(value: RichText) = JSONObject().put("text", value.text).put("spans", JSONArray().apply {
        value.spans.forEach { span ->
            put(JSONObject().put("start", span.start).put("end", span.end)
                .put("styles", JSONArray(span.styles.map { it.name }))
                .put("foregroundArgb", span.foregroundArgb ?: JSONObject.NULL)
                .put("backgroundArgb", span.backgroundArgb ?: JSONObject.NULL)
                .put("link", span.link ?: JSONObject.NULL))
        }
    })

    private fun richTextFromJson(json: JSONObject): RichText {
        val text = json.optString("text")
        val spans = json.optJSONArray("spans") ?: JSONArray()
        return RichText(text, (0 until spans.length()).map { index ->
            val item = spans.getJSONObject(index)
            val styles = item.optJSONArray("styles") ?: JSONArray()
            InlineSpan(
                start = item.getInt("start"),
                end = item.getInt("end"),
                styles = (0 until styles.length()).mapNotNull { runCatching {
                    InlineStyle.valueOf(styles.getString(it))
                }.getOrNull() }.toSet(),
                foregroundArgb = item.longOrNull("foregroundArgb"),
                backgroundArgb = item.longOrNull("backgroundArgb"),
                link = item.stringOrNull("link")
            )
        })
    }

    private fun listItemsToJson(items: List<ListItem>) = JSONArray().apply {
        items.forEach { item -> put(JSONObject().put("id", item.id)
            .put("content", richTextToJson(item.content)).put("children", listItemsToJson(item.children))) }
    }

    private fun listItemsFromJson(array: JSONArray): List<ListItem> = (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        ListItem(item.optString("id").ifBlank { newBlockId() },
            richTextFromJson(item.getJSONObject("content")),
            listItemsFromJson(item.optJSONArray("children") ?: JSONArray()))
    }

    private fun checklistItemsToJson(items: List<ChecklistItem>) = JSONArray().apply {
        items.forEach { item -> put(JSONObject().put("id", item.id).put("content", richTextToJson(item.content))
            .put("checked", item.checked).put("children", checklistItemsToJson(item.children))) }
    }

    private fun checklistItemsFromJson(array: JSONArray): List<ChecklistItem> = (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        ChecklistItem(item.optString("id").ifBlank { newBlockId() },
            richTextFromJson(item.getJSONObject("content")), item.optBoolean("checked"),
            checklistItemsFromJson(item.optJSONArray("children") ?: JSONArray()))
    }

    private fun blockFromJson(json: JSONObject): DocumentBlock {
        val id = json.optString("id").ifBlank { newBlockId() }
        return when (json.getString("type")) {
            "paragraph" -> ParagraphBlock(id, richTextFromJson(json.getJSONObject("content")))
            "heading" -> HeadingBlock(id, json.getInt("level"), richTextFromJson(json.getJSONObject("content")))
            "list" -> ListBlock(id, json.optBoolean("ordered"), listItemsFromJson(json.optJSONArray("items") ?: JSONArray()))
            "checklist" -> ChecklistBlock(id, checklistItemsFromJson(json.optJSONArray("items") ?: JSONArray()))
            "quote" -> QuoteBlock(id, richTextFromJson(json.getJSONObject("content")))
            "code" -> CodeBlock(id, json.optString("code"), json.stringOrNull("language"))
            "table" -> {
                val rows = json.getJSONArray("rows")
                TableBlock(id, (0 until rows.length()).map { rowIndex ->
                    val row = rows.getJSONObject(rowIndex)
                    val cells = row.getJSONArray("cells")
                    TableRow(row.optString("id").ifBlank { newBlockId() },
                        (0 until cells.length()).map { TableCell(richTextFromJson(cells.getJSONObject(it))) })
                })
            }
            "image" -> ImageBlock(id, json.getString("assetId"), json.optDouble("widthFraction", 1.0).toFloat(),
                enumValueOfOrDefault(json.optString("alignment"), ImageAlignment.CENTER), json.optString("caption"))
            "attachment" -> AttachmentBlock(id, json.getString("assetId"), json.getString("fileName"),
                json.stringOrNull("mimeType"), json.longOrNull("sizeBytes"))
            "link" -> LinkBlock(id, json.optString("label"), json.getString("url"))
            "callout" -> CalloutBlock(id, enumValueOfOrDefault(json.optString("kind"), CalloutKind.NOTE),
                json.optString("title"), richTextFromJson(json.getJSONObject("content")))
            "divider" -> DividerBlock(id)
            else -> error("Unknown document block type: ${json.optString("type")}")
        }
    }

    private fun documentFromJson(json: JSONObject): EditorDocument {
        val version = json.getInt("schemaVersion")
        require(version == CURRENT_DOCUMENT_SCHEMA_VERSION) {
            "Unsupported document schema version: $version"
        }
        val metadataJson = json.optJSONObject("metadata") ?: JSONObject()
        val tagsJson = metadataJson.optJSONArray("tags") ?: JSONArray()
        val blocksJson = json.getJSONArray("blocks")
        return EditorDocument(
            id = json.getString("id"),
            schemaVersion = version,
            createdAt = json.getLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
            metadata = DocumentMetadata(metadataJson.optString("title"),
                (0 until tagsJson.length()).map { tagsJson.getString(it) }),
            blocks = (0 until blocksJson.length()).map { blockFromJson(blocksJson.getJSONObject(it)) }
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOfOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

    private fun JSONObject.longOrNull(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)
}
