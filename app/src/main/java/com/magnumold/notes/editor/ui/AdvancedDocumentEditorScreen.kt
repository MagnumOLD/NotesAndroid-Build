package com.magnumold.notes.editor.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.content.FileProvider
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.testTag
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
import com.magnumold.notes.editor.model.TableCell
import com.magnumold.notes.editor.model.TableRow
import com.magnumold.notes.editor.storage.DocumentAssetStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Compose editor for ordinary notes. Task records continue to use their
 * dedicated legacy task editor and are never routed through this screen.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun AdvancedDocumentEditorScreen(
    document: EditorDocument,
    onDocumentChange: (EditorDocument) -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit = {},
    onShare: () -> Unit = {},
    autoFocus: Boolean = false
) {
    val context = LocalContext.current
    val assetStore = remember(context) { DocumentAssetStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val controller = remember(document.id) { EditorSessionController(document) }
    var editorState by remember(document.id) { mutableStateOf(controller.state) }
    // Toolbar lambdas are separate restart scopes; derive history flags from the
    // observable editor state so they refresh after text edits and undo/redo.
    val canUndo = remember(editorState) { controller.canUndo }
    val canRedo = remember(editorState) { controller.canRedo }
    var showLinkDialog by remember(document.id) { mutableStateOf(false) }
    var newLinkLabel by remember(document.id) { mutableStateOf("") }
    var newLinkUrl by remember(document.id) { mutableStateOf("https://") }
    var overflowExpanded by remember(document.id) { mutableStateOf(false) }

    fun publish(next: com.magnumold.notes.editor.engine.EditorSessionState) {
        val previousDocument = editorState.document
        editorState = next
        if (next.document != previousDocument) onDocumentChange(next.document)
    }

    fun insert(block: DocumentBlock) {
        publish(controller.execute(DocumentCommand.Insert(editorState.document.blocks.size, block)))
    }

    fun importSelectedAsset(uri: android.net.Uri) {
        scope.launch {
            try {
                val block = withContext(Dispatchers.IO) {
                    val name = context.contentResolver.query(
                        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Вложение"
                    val asset = assetStore.import(uri, name)
                    if (asset.mimeType?.startsWith("image/") == true) {
                        ImageBlock(assetId = asset.assetId)
                    } else {
                        AttachmentBlock(
                            assetId = asset.assetId,
                            fileName = asset.fileName,
                            mimeType = asset.mimeType,
                            sizeBytes = asset.sizeBytes
                        )
                    }
                }
                insert(block)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Toast.makeText(context, error.message ?: "Не удалось добавить файл", Toast.LENGTH_LONG).show()
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importSelectedAsset(uri)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importSelectedAsset(uri)
    }

    fun toggleInlineStyle(style: InlineStyle) {
        publish(controller.applyInlineStyle(style, enabled = !controller.isInlineStyleActive(style)))
    }

    Scaffold(
        modifier = Modifier.testTag("advanced-document-editor").imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(document.metadata.title.ifBlank { "Заметка" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { publish(controller.undo()) },
                        enabled = canUndo
                    ) { Icon(Icons.Outlined.Undo, contentDescription = "Отменить") }
                    IconButton(onClick = { publish(controller.redo()) }, enabled = canRedo) {
                        Icon(Icons.Outlined.Redo, contentDescription = "Повторить")
                    }
                    IconButton(onClick = { overflowExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Дополнительно")
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Поделиться") },
                            onClick = {
                                overflowExpanded = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить заметку") },
                            onClick = {
                                overflowExpanded = false
                                onDelete()
                            }
                        )
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
                    IconButton(onClick = { toggleInlineStyle(InlineStyle.BOLD) }) {
                        Icon(Icons.Outlined.FormatBold, contentDescription = "Жирный")
                    }
                    IconButton(onClick = { toggleInlineStyle(InlineStyle.ITALIC) }) {
                        Icon(Icons.Outlined.FormatItalic, contentDescription = "Курсив")
                    }
                    IconButton(onClick = { toggleInlineStyle(InlineStyle.UNDERLINE) }) {
                        Icon(Icons.Outlined.FormatUnderlined, contentDescription = "Подчёркнутый")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { insert(ParagraphBlock()) }) { Text("Текст") }
                    TextButton(onClick = { insert(HeadingBlock(level = 1, content = RichText(""))) }) { Text("Заголовок") }
                    TextButton(onClick = { insert(ListBlock(ordered = false, items = listOf(ListItem(content = RichText(""))))) }) { Text("Список") }
                    TextButton(onClick = { insert(ChecklistBlock(items = listOf(ChecklistItem(content = RichText(""))))) }) { Text("Чек-лист") }
                    TextButton(onClick = { insert(QuoteBlock(content = RichText(""))) }) { Text("Цитата") }
                    TextButton(onClick = { insert(CodeBlock(code = "")) }) { Text("Код") }
                    TextButton(onClick = {
                        insert(
                            TableBlock(
                                rows = listOf(
                                    TableRow(cells = listOf(TableCell(), TableCell())),
                                    TableRow(cells = listOf(TableCell(), TableCell()))
                                )
                            )
                        )
                    }) { Text("Таблица") }
                    TextButton(onClick = {
                        newLinkLabel = ""
                        newLinkUrl = "https://"
                        showLinkDialog = true
                    }) { Text("Ссылка") }
                    TextButton(onClick = { insert(CalloutBlock(kind = CalloutKind.NOTE, content = RichText(""))) }) { Text("Заметка-блок") }
                    TextButton(onClick = { insert(DividerBlock()) }) { Text("Разделитель") }
                    TextButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) { Text("Изображение") }
                    TextButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("Файл") }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val firstEditableBlockId = editorState.document.blocks.firstOrNull { block ->
                block is ParagraphBlock || block is HeadingBlock || block is QuoteBlock ||
                    block is CalloutBlock || block is CodeBlock || block is ListBlock ||
                    block is ChecklistBlock || block is TableBlock
            }?.id
            editorState.document.blocks.forEach { block ->
                DocumentBlockEditor(
                    block = block,
                    controller = controller,
                    onStateChange = ::publish,
                    assetStore = assetStore,
                    autoFocus = autoFocus && block.id == firstEditableBlockId,
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

    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Добавить ссылку") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newLinkLabel,
                        onValueChange = { newLinkLabel = it },
                        label = { Text("Текст") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newLinkUrl,
                        onValueChange = { newLinkUrl = it },
                        label = { Text("Адрес") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val url = newLinkUrl.trim()
                        if (url.startsWith("https://") || url.startsWith("http://")) {
                            insert(LinkBlock(label = newLinkLabel.trim().ifBlank { url }, url = url))
                            showLinkDialog = false
                        }
                    },
                    enabled = newLinkUrl.trim().startsWith("https://") || newLinkUrl.trim().startsWith("http://")
                ) { Text("Добавить") }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun DocumentBlockEditor(
    block: DocumentBlock,
    controller: EditorSessionController,
    onStateChange: (com.magnumold.notes.editor.engine.EditorSessionState) -> Unit,
    assetStore: DocumentAssetStore,
    autoFocus: Boolean,
    onToggleChecklist: (String, Boolean) -> Unit
) {
    when (block) {
        is ParagraphBlock -> RichTextEditorField(block.id, null, block.content, controller, onStateChange, 16.sp, autoFocus = autoFocus)
        is HeadingBlock -> RichTextEditorField(
            block.id, null, block.content, controller, onStateChange,
            when (block.level) { 1 -> 26.sp; 2 -> 22.sp; else -> 19.sp },
            FontWeight.SemiBold,
            autoFocus = autoFocus
        )
        is QuoteBlock -> Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.width(3.dp).height(42.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(12.dp))
            RichTextEditorField(block.id, null, block.content, controller, onStateChange, 16.sp, autoFocus = autoFocus)
        }
        is CalloutBlock -> Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(block.title.ifBlank { block.kind.label() }, style = MaterialTheme.typography.labelLarge)
                RichTextEditorField(block.id, null, block.content, controller, onStateChange, 16.sp, autoFocus = autoFocus)
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
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(block.language ?: "Код", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RichTextEditorField(
                    block.id, null, RichText(block.code), controller, onStateChange, 14.sp,
                    fontFamily = FontFamily.Monospace,
                    autoFocus = autoFocus
                )
            }
        }
        is TableBlock -> Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            block.rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    row.cells.forEachIndexed { columnIndex, cell ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            RichTextEditorField(
                                block.id, null, cell.content, controller, onStateChange, 14.sp,
                                tableCell = row.id to columnIndex,
                                autoFocus = autoFocus && row == block.rows.first() && columnIndex == 0
                            )
                        }
                    }
                }
            }
        }
        is ImageBlock -> ImageAssetBlock(block, assetStore)
        is AttachmentBlock -> AttachmentAssetBlock(block, assetStore)
        is LinkBlock -> LinkBlockEditor(block, controller, onStateChange)
        is DividerBlock -> Divider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun LinkBlockEditor(
    block: LinkBlock,
    controller: EditorSessionController,
    onStateChange: (com.magnumold.notes.editor.engine.EditorSessionState) -> Unit
) {
    var urlDraft by remember(block.id) { mutableStateOf(block.url) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = block.label,
            onValueChange = { value ->
                onStateChange(controller.execute(DocumentCommand.Replace(block.copy(label = value))))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Текст ссылки") },
            singleLine = true
        )
        OutlinedTextField(
            value = urlDraft,
            onValueChange = { value ->
                urlDraft = value
                if (value.startsWith("https://") || value.startsWith("http://")) {
                    onStateChange(controller.execute(DocumentCommand.Replace(block.copy(url = value))))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Адрес") },
            singleLine = true
        )
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
    fontWeight: FontWeight = FontWeight.Normal,
    fontFamily: FontFamily = FontFamily.Default,
    tableCell: Pair<String, Int>? = null,
    autoFocus: Boolean = false
) {
    val focusRequester = remember(blockId, itemId, tableCell) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    var selection by remember(blockId, itemId, tableCell) { mutableStateOf(TextRange(richText.text.length)) }
    LaunchedEffect(richText.text) {
        if (selection.end > richText.text.length) selection = TextRange(richText.text.length)
    }
    val annotated = remember(richText) { richText.toAnnotatedString() }
    BasicTextField(
        value = TextFieldValue(annotated, selection),
        onValueChange = { next ->
            val oldText = richText.text
            val newText = next.text
            val targetCell = tableCell?.let {
                com.magnumold.notes.editor.engine.TableCellPosition(it.first, it.second)
            }
            val modelText = controller.textForTarget(blockId, itemId, targetCell)
            if (modelText != oldText) {
                // Undo/redo may update the model while the IME still has an older
                // composing value. Ignore that stale callback instead of restoring
                // text which the user has just undone.
                val currentLength = modelText?.length ?: 0
                selection = TextRange(
                    next.selection.start.coerceIn(0, currentLength),
                    next.selection.end.coerceIn(0, currentLength)
                )
            } else {
                selection = next.selection
                if (newText != oldText) {
                    if (controller.state.activeBlockId != blockId || controller.state.activeItemId != itemId ||
                        (targetCell != null && controller.state.activeTableCell != targetCell)) {
                        if (tableCell != null) controller.activateTableCell(blockId, tableCell.first, tableCell.second)
                        else if (itemId == null) controller.activateBlock(blockId)
                        else if (controller.state.document.blocks.firstOrNull { it.id == blockId } is ListBlock) {
                            controller.activateListItem(blockId, itemId)
                        } else {
                            controller.activateChecklistItem(blockId, itemId)
                        }
                    }
                    val change = textChange(oldText, newText)
                    controller.setSelection(change.start, change.oldEnd)
                    onStateChange(controller.replaceSelection(change.replacement))
                } else {
                    if (controller.state.activeBlockId != blockId || controller.state.activeItemId != itemId ||
                        (targetCell != null && controller.state.activeTableCell != targetCell)) {
                        if (tableCell != null) controller.activateTableCell(blockId, tableCell.first, tableCell.second)
                        else if (itemId == null) controller.activateBlock(blockId)
                        else if (controller.state.document.blocks.firstOrNull { it.id == blockId } is ListBlock) {
                            controller.activateListItem(blockId, itemId)
                        } else controller.activateChecklistItem(blockId, itemId)
                    }
                    onStateChange(controller.setSelection(next.selection.start, next.selection.end))
                }
            }
        },
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
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
private fun ImageAssetBlock(block: ImageBlock, assetStore: DocumentAssetStore) {
    val bitmap by produceState<Bitmap?>(initialValue = null, block.assetId) {
        value = withContext(Dispatchers.IO) {
            assetStore.find(block.assetId)?.let(::decodeDocumentImagePreview)
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = block.caption.ifBlank { "Изображение" },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("Изображение недоступно", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (block.caption.isNotBlank()) Text(block.caption, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun decodeDocumentImagePreview(file: java.io.File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 1600 || bounds.outHeight / sampleSize > 1600) sampleSize *= 2
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
}

@Composable
private fun AttachmentAssetBlock(block: AttachmentBlock, assetStore: DocumentAssetStore) {
    val context = LocalContext.current
    val file = remember(block.assetId) { assetStore.find(block.assetId) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable {
            if (file == null) {
                Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    val uri = FileProvider.getUriForFile(
                        context, "${context.packageName}.document-assets", file
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, block.mimeType ?: "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "Нет приложения для открытия этого файла", Toast.LENGTH_LONG).show()
                } catch (error: Exception) {
                    Toast.makeText(context, error.message ?: "Не удалось открыть файл", Toast.LENGTH_LONG).show()
                }
            }
        },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(block.fileName, style = MaterialTheme.typography.labelLarge)
            val details = listOfNotNull(block.mimeType, block.sizeBytes?.let(::formatAssetSize)).joinToString(" · ")
            if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Нажмите, чтобы открыть", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun formatAssetSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    else -> String.format(java.util.Locale.ROOT, "%.1f МБ", bytes / (1024f * 1024f))
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
