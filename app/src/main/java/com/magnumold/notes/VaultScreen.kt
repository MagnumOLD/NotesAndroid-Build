package com.magnumold.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultScreen(
    title: String,
    entries: List<VaultNote>,
    isTrash: Boolean,
    onBack: () -> Unit,
    onRestore: (VaultNote) -> Unit,
    onMoveToTrash: (VaultNote) -> Unit,
    onDeleteForever: (VaultNote) -> Unit,
    onEmptyTrash: () -> Unit,
    themeColors: CustomThemeColors
) {
    var confirmEmpty by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppPageTopBar(
                backgroundColor = themeColors.topBar,
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(NoteIcon.Back, "Назад") } },
                title = { Text(title, style = AppTypography.screenTitleStyle()) },
                actions = {
                    if (isTrash && entries.isNotEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }) { Text("Очистить") }
                    }
                }
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(if (isTrash) "Корзина пуста" else "Архив пуст", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = AppPageLayout.standardListContentPadding) {
                items(entries, key = { it.note.id }) { entry ->
                    val preview = entry.note.blocks.asSequence().mapNotNull { block ->
                        block.text.lineSequence().firstOrNull { it.isNotBlank() }
                    }.firstOrNull() ?: "Пустая заметка"
                    RecordCardSurface(background = MaterialTheme.colorScheme.background) {
                        Column(
                            Modifier.fillMaxWidth()
                                .heightIn(min = CardDimensions.contentHeight)
                                .padding(
                                    horizontal = AppSpacing.screenHorizontal,
                                    vertical = AppLayoutContract.mainCardVerticalInset
                                )
                        ) {
                            Text(preview, style = AppTypography.cardPreviewStyle(), maxLines = 2)
                            Text(
                                if (entry.collectionId == null) "Основной блокнот" else "Подборка",
                                style = AppTypography.metadataStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = AppSpacing.xxs)
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = AppSpacing.xs),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!isTrash) TextButton(onClick = { onMoveToTrash(entry) }) { Text("Удалить") }
                                if (isTrash) TextButton(onClick = { onDeleteForever(entry) }) { Text("Удалить навсегда") }
                                TextButton(onClick = { onRestore(entry) }) { Text("Восстановить") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmEmpty) AlertDialog(
        onDismissRequest = { confirmEmpty = false },
        title = { Text("Очистить корзину?") },
        text = { Text("Все заметки будут удалены без возможности восстановления.") },
        confirmButton = { TextButton(onClick = { onEmptyTrash(); confirmEmpty = false }) { Text("Очистить") } },
        dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Отмена") } }
    )
}
