package com.magnumold.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal data class MigrationStartupState(
    val isRunning: Boolean = true,
    val isReady: Boolean = false,
    val isMigrationComplete: Boolean = false,
    val error: String? = null
)

@Composable
internal fun MigrationStartupScreen(
    state: MigrationStartupState,
    onRetry: () -> Unit,
    onContinueLegacy: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (state.isRunning) {
                CircularProgressIndicator()
                Text("Подготавливаем документы…", style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    "Не удалось безопасно подготовить документы",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    state.error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onRetry) { Text("Повторить") }
                Button(onClick = onContinueLegacy) { Text("Продолжить со старым редактором") }
            }
        }
    }
}
