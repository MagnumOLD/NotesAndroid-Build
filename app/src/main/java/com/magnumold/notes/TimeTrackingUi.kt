package com.magnumold.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared flat section used by time-tracking controls.
 * Keeps the same page guides, spacing and subtle separators as NotesAndroid.
 */
@Composable
internal fun TimeTrackingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = AppSpacing.screenHorizontal)
            .padding(vertical = AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
        HorizontalDivider(
            modifier = Modifier.padding(top = AppSpacing.xs),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
            thickness = CardDimensions.dividerThickness
        )
    }
}
