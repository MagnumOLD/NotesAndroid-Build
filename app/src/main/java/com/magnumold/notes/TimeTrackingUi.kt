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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
            .padding(top = AppSpacing.xs, bottom = AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
    ) {
        Text(
            title,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        content()
        HorizontalDivider(
            modifier = Modifier.padding(top = AppSpacing.xs),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            thickness = CardDimensions.dividerThickness
        )
    }
}
