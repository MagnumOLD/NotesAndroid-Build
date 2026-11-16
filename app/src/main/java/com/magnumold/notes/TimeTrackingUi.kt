package com.magnumold.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/** Compact, flat time summary without a bottom separator. */
@Composable
internal fun TimeTrackingSection(
    title: String,
    headerAction: (@Composable () -> Unit)? = null,
    trailingContent: @Composable RowScope.() -> Unit = {},
    contentSpacing: Dp = AppSpacing.xs,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .appPageHorizontalInsets(),
            verticalArrangement = Arrangement.spacedBy(contentSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .height(AppLayoutContract.timeTrackingHeaderRowHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = AppTypography.sectionLabelEmphasisStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                trailingContent()
                headerAction?.invoke()
            }
            content()
        }
    }
}
