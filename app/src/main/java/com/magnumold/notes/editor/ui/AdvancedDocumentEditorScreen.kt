package com.magnumold.notes.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magnumold.notes.editor.engine.DocumentCommand
import com.magnumold.notes.editor.engine.EditorSessionController
import com.magnumold.notes.editor.model.AttachmentBlock
import com.magnumold.notes.editor.model.CalloutBlock
import com.magnumold.notes.editor.model.CalloutKind
import com.magnumold.notes.editor.model.ChecklistBlock
import com.magnumold.notes.editor.model.ChecklistItem
import com.magnumold.notes.editor.model.CodeBlock
import com.magnumold.notes.editor.model.DividerBlock
import com.magnumold.notes.editor.model.DocumentBlock
import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.model.HeadingBlock
import com.magnumold.notes.editor.model.ImageBlock
import com.magnumold.notes.editor.model.InlineSpan
import com.magnumold.notes.editor.model.InlineStyle
import com.magnumold.notes.editor.model.LinkBlock
import com.magnumold.notes.editor.model.ListBlock
import com.magnumold.notes.editor.model.ListItem
import com.magnumold.notes.editor.model.ParagraphBlock
import com.magnumold.notes.editor.model.QuoteBlock
import com.magnumold.notes.editor.model.RichText
import com.magnumold.notes.editor.model.TableBlock

/**
 * First independent Compose surface for the advanced document editor.
 * It is intentionally not wired into NotesApp yet: the legacy note/task screen
 * remains the production route until persistence, migration and UI gates pass.
 */
@Composable
internal fun AdvancedDocumentEditorScreen(
    document: EditorDocument,
    onDocumentChange: (EditorDocument) -> Unit,
    onBack: () -> Unit
) {
    val controller = remember(document.id) { EditorSessionController(document) }
    var editorState by remember(document.id) { mutableStateOf(controller.state) }

    LaunchedEffect(document.id, document.updatedAt) {
        if (document != editorState.document && document.id == editorState.document.id) {
            // External persistence updates are accepted only when they differ from our last emission.
            if (document.blocks != editorState.document.blocks) {
                editorState = EditorSessionController(document).state
            }
        }
    }

    fun publish(next: com.magnumold.notes.editor.engine.EditorSessionState) {
        editorState = next
        onDocumentChange(next.document)
    }

    fun insert(block: DocumentBlock) {
        publish(controller.execute(DocumentCommand.Insert(editorState.document.blocks.size, block)))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document.metadata.title.ifBlank { "Документ" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { publish(controller.undo()) },
                        enabled = editorState.document != document || editorState.selection.anchor >= 0
                    ) { Icon(Icons.Outlined.Undo, contentDescription = "Отменить") }
                    IconButton(onClick = { publish(controller.redo()) }) {
                        Icon(Icons.Outlined.Redo, contentDescription = "Повторить")
                    }
                }
            )
        },
        bottomBar = {
            Column {
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { publish(controller.applyInlineStyle(InlineStyle.BOLD, true)) }) {
                        Icon(Icons.Outlined.FormatBold, contentDescription = "Жирный")
                    }
                    IconButton(onClick = { publish(controller.applyInlineStyle(InlineStyle.ITALIC, true)) }) {
                        Icon(Icons.Outlined.FormatItalic, contentDescription = "Курсив")
                    }
                    IconButton(onClick = { publish(controller.applyInlineStyle(InlineStyle.UNDERLINE, true)) }) {
                        Icon(Icons.Outlined.FormatUnderlined, contentDescription = "Подчёркнутый")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { insert(ParagraphBlock()) }) { Text("Текст") }
                    TextButton(onClick = { insert(HeadingBlock(level = 1, content = RichText(""))) }) { Text("Заголовок") }
                    TextButton(onClick = { insert(ListBlock(ordered = false, items = listOf(ListItem(content = RichText(""))))) }) { Text("Список") }
                    TextButton(onClick = { insert(ChecklistBlock(items = listOf(ChecklistItem(content = RichText(""))))) }) { Text("Чек-лист") }
                    TextButton(onClick = { insert(QuoteBlock(content = RichText(""))) }) { Text("Цитата") }
                    TextButton(onClick = { insert(CodeBlock(code = "")) }) { Text("Код") }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            editorState.document.blocks.forEach { block ->
                DocumentBlockEditor(
                    block = block,
                    controller = controller,
                    onStateChange = ::publish,
                    onToggleChecklist = { itemId, checked ->
                        val updated = block.toggleChecklistItem(itemId, checked)
                        publish(controller.execute(DocumentCommand.Replace(updated)))
                    }
                )
            }
            TextButton(onClick = { insert(ParagraphBlock()) }) {
                Text("Добавить абзац")
            }
        }
    }
}

@Composable
private fun DocumentBlockEditor(
    block: DocumentBlock,
    controller: EditorSessionController,
    onStateChange: (com.magnumold.notes.editor.engine.EditorSessionState) -> Unit,
    onToggleChecklist: (String, Boolean) -> Unit
) {
    when (block) {
        is ParagraphBlock -> RichTextEditorField(block.id, null, block.content, controller, onStateChange, 16.sp)
        is HeadingBlock -> RichTextEditorField(
            block.id, null, block.content, controller, onStateChange,
            when (block.level) { 1 -> 26.sp; 2 -> 22.sp; else -> 19.sp },
            FontWeight.SemiBold
        )
        is QuoteBlock -> Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.width(3.dp).height(42.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(12.dp))
            RichTextEditorField(block.id, null, block.content, controller, onStateChange, 16.sp)
        }
        is CalloutBlock -> Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(block.title.ifBlank { block.kind.label() }, style = MaterialTheme.typography.labelLarge)
                RichTextEditorField(block.id, null, block.content, controller, onStateChange, 16.sp)
            }
        }
        is ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.items.forEachIndexed { index, item ->
                ListItemEditor(block.id, item, index, block.ordered, 0, controller, onStateChange)
            }
        }
        is ChecklistBlock -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.items.forEach { item ->
                ChecklistItemEditor(block.id, item, 0, controller, onStateChange, onToggleChecklist)
            }
        }
        is CodeBlock -> Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(block.language ?: "Код", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(block.code.ifBlank { "Пустой блок кода" }, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            }
        }
        is TableBlock -> Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            block.rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    row.cells.forEach { cell ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            Text(cell.content.text, Modifier.padding(8.dp), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        is ImageBlock -> PlaceholderBlock("Изображение", block.caption.ifBlank { "Вложение: ${block.assetId}" })
        is AttachmentBlock -> PlaceholderBlock("Файл", block.fileName)
        is LinkBlock -> PlaceholderBlock("Ссылка", block.label.ifBlank { block.url } + "\n" + block.url)
        is DividerBlock -> Divider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun RichTextEditorField(
    blockId: String,
    itemId: String?,
    richText: RichText,
    controller: EditorSessionController,
    onStateChange: (com.magnumold.notes.editor.engine.EditorSessionState) -> Unit,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight = FontWeight.Normal
) {
    var selection by remember(blockId, itemId) { mutableStateOf(TextRange(richText.text.length)) }
    LaunchedEffect(richText.text) {
        if (selection.end > richText.text.length) selection = TextRange(richText.text.length)
    }
    val annotated = remember(richText) { richText.toAnnotatedString() }
    BasicTextField(
        value = TextFieldValue(annotated, selection),
        onValueChange = { next ->
            val oldText = richText.text
            val newText = next.text
            selection = next.selection
            if (newText != oldText) {
                if (controller.state.activeBlockId != blockId || controller.state.activeItemId != itemId) {
                    if (itemId == null) controller.activateBlock(blockId)
                    else if (controller.state.document.blocks.firstOrNull { it.id == blockId } is ListBlock) {
                        controller.activateListItem(blockId, itemId)
                    } else {
                        controller.activateChecklistItem(blockId, itemId)
                    }
                }
                val change = textChange(oldText, newText)
                controller.setSelection(change.first, oldText.length - change.second)
                onStateChange(controller.replaceSelection(change.third))
            } else {
                if (controller.state.activeBlockId != blockId || controller.state.activeItemId != itemId) {
                    if (itemId == null) controller.activateBlock(blockId)
                    else if (controller.state.document.blocks.firstOrNull { it.id == blockId } is ListBlock) {
                        controller.activateListItem(blockId, itemId)
                    } else controller.activateChecklistItem(blockId, itemId)
                }
                onStateChange(controller.setSelection(next.selection.start, next.selection.end))
            }
        },
        modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = fontSize,
            fontWeight = fontWeight,
            lineHeight = fontSize * 1.45f
        ),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Box {
                if (richText.text.isEmpty()) {
                    Text("Начните писать…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = fontSize)
                }
                inner()
            }
        }
    )
}

@Composable
private fun ListItemEditor(
    blockId: String,
    item: ListItem,
    index: Int,
    ordered: Boolean,
    depth: Int,
    controller: EditorSessionController,
    onStateChange: (com.magnumold.notes.editor.engine.EditorSessionState) -> Unit
) {
    Column(Modifier.padding(start = (depth * 18).dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(if (ordered) "${index + 1}." else "•", Modifier.width(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            RichTextEditorField(blockId, item.id, item.content, controller, onStateChange, 16.sp)
        }
        item.children.forEachIndexed { childIndex, child ->
            ListItemEditor(blockId, child, childIndex, ordered, depth + 1, controller, onStateChange)
        }
    }
}

@Composable
private fun ChecklistItemEditor(
    blockId: String,
    item: ChecklistItem,
    depth: Int,
    controller: EditorSessionController,
    onStateChange: (com.magnumold.notes.editor.engine.EditorSessionState) -> Unit,
    onToggle: (String, Boolean) -> Unit
) {
    Column(Modifier.padding(start = (depth * 18).dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.checked, onCheckedChange = { onToggle(item.id, it) }, modifier = Modifier.size(40.dp))
            RichTextEditorField(blockId, item.id, item.content, controller, onStateChange, 16.sp)
        }
        item.children.forEach { child ->
            ChecklistItemEditor(blockId, child, depth + 1, controller, onStateChange, onToggle)
        }
    }
}

@Composable
private fun PlaceholderBlock(title: String, detail: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class TextChange(val start: Int, val oldEnd: Int, val replacement: String)

private fun textChange(oldText: String, newText: String): TextChange {
    var prefix = 0
    val commonLimit = minOf(oldText.length, newText.length)
    while (prefix < commonLimit && oldText[prefix] == newText[prefix]) prefix++
    var suffix = 0
    while (suffix < oldText.length - prefix && suffix < newText.length - prefix &&
        oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
    ) suffix++
    return TextChange(prefix, oldText.length - suffix, newText.substring(prefix, newText.length - suffix))
}

private fun RichText.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    append(text)
    spans.forEach { span ->
        val style = SpanStyle(
            fontWeight = if (InlineStyle.BOLD in span.styles) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (InlineStyle.ITALIC in span.styles) FontStyle.Italic else FontStyle.Normal,
            textDecoration = when {
                InlineStyle.UNDERLINE in span.styles && InlineStyle.STRIKETHROUGH in span.styles ->
                    TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                InlineStyle.UNDERLINE in span.styles -> TextDecoration.Underline
                InlineStyle.STRIKETHROUGH in span.styles -> TextDecoration.LineThrough
                else -> TextDecoration.None
            },
            fontFamily = if (InlineStyle.CODE in span.styles) FontFamily.Monospace else FontFamily.Default
        )
        addStyle(style, span.start.coerceAtLeast(0), span.end.coerceAtMost(text.length))
    }
}

private fun DocumentBlock.toggleChecklistItem(itemId: String, checked: Boolean): DocumentBlock {
    if (this !is ChecklistBlock) return this
    fun update(items: List<ChecklistItem>): List<ChecklistItem> = items.map { item ->
        when {
            item.id == itemId -> item.copy(checked = checked)
            else -> item.copy(children = update(item.children))
        }
    }
    return copy(items = update(items))
}

private fun CalloutKind.label(): String = when (this) {
    CalloutKind.NOTE -> "Примечание"
    CalloutKind.TIP -> "Совет"
    CalloutKind.WARNING -> "Предупреждение"
    CalloutKind.IMPORTANT -> "Важно"
}
