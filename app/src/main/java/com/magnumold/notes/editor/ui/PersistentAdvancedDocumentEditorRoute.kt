package com.magnumold.notes.editor.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.magnumold.notes.editor.model.EditorDocument
import com.magnumold.notes.editor.storage.DocumentPersistenceController
import com.magnumold.notes.editor.storage.DocumentRepository
import kotlinx.coroutines.launch

/**
 * Lifecycle-aware entry point for the advanced editor.
 *
 * The route deliberately asks before restoring a recovery draft. The caller can
 * keep the legacy editor active until the migration gate has completed.
 */
@Composable
internal fun PersistentAdvancedDocumentEditorRoute(
    initialDocument: EditorDocument,
    onDocumentChange: (EditorDocument) -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { DocumentRepository(context.applicationContext) }
    val persistence = remember(repository, initialDocument.id, scope) {
        DocumentPersistenceController(repository, scope)
    }
    val state by persistence.state.collectAsState()
    var loading by remember(initialDocument.id) { mutableStateOf(true) }
    var editorReady by remember(initialDocument.id) { mutableStateOf(false) }

    LaunchedEffect(initialDocument.id, persistence) {
        val opened = persistence.open(initialDocument)
        loading = false
        editorReady = opened.recoveryDraft == null
        opened.storageError?.let {
            Toast.makeText(context, "Хранилище документа: $it", Toast.LENGTH_LONG).show()
        }
    }

    DisposableEffect(lifecycleOwner, persistence) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                scope.launch {
                    persistence.flush().exceptionOrNull()?.let { error ->
                        Toast.makeText(
                            context,
                            error.message ?: "Не удалось сохранить документ",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (!editorReady && state.recoveryDraft != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Восстановить черновик?") },
            text = {
                Text(
                    "Найден более поздний несохранённый вариант документа. " +
                        "Основная версия пока не изменена."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    persistence.restoreRecoveryDraft()
                    editorReady = true
                }) { Text("Восстановить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        persistence.discardRecoveryDraft()
                        editorReady = true
                    }
                }) { Text("Оставить основную версию") }
            }
        )
    } else {
        val document = state.document ?: initialDocument
        AdvancedDocumentEditorScreen(
            document = document,
            onDocumentChange = { changed ->
                persistence.update(changed)
                onDocumentChange(changed)
            },
            onBack = {
                scope.launch {
                    val result = persistence.flush()
                    if (result.isSuccess) {
                        onBack()
                    } else {
                        Toast.makeText(
                            context,
                            result.exceptionOrNull()?.message ?: "Не удалось сохранить документ",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            onDelete = {
                scope.launch {
                    val result = persistence.flush()
                    if (result.isSuccess) {
                        onDelete()
                    } else {
                        Toast.makeText(
                            context,
                            result.exceptionOrNull()?.message ?: "Не удалось сохранить документ",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            onShare = onShare
        )
    }
}
