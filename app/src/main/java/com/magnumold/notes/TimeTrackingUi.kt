package com.magnumold.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Shared flat section; content follows page guides while separators run edge to edge. */
@Composable
internal fun TimeTrackingSection(
    title: String,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .appPageHorizontalInsets()
                .padding(top = AppSpacing.xxs, bottom = AppSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                title,
                style = AppTypography.sectionLabelStyle().copy(
                    lineHeight = AppTypography.sectionLabelLineHeight,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface
                )
                headerAction?.invoke()
            }
            content()
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = AppSpacing.xxs),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            thickness = CardDimensions.dividerThickness
        )
    }
}
