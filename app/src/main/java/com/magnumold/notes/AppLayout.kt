package com.magnumold.notes

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared geometry for every primary circular add button. */
internal object PrimaryAddButtonDimensions {
    val diameter = 56.dp
    val verticalOffset = (-65).dp
}

internal object AppSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp

    val screenHorizontal = lg
    val screenVertical = md
    val toolbarControlInset = xxs
    // Material3 ListItem has a 16.dp default content inset; 4.dp outside
    // aligns its visible content with the app's 20.dp page guide.
    val settingsListItemOuterInset = xxs
}


/**
 * App-wide typography tokens. Screen-specific hierarchy may differ, but the
 * underlying sizes and line heights are defined here rather than duplicated.
 */
internal object AppTypography {
    val screenTitle = 18.sp
    val editorTitle = 18.sp
    val sectionHeading = 17.sp
    val body = 16.sp
    val cardPreview = 16.sp
    val cardPreviewLineHeight = 22.sp
    val helper = 13.sp
    val bodyLineHeight = 24.sp
    val sectionLabel = 14.sp
    val sectionLabelLineHeight = 20.sp
    val metadata = 12.sp
}

/** Shared page/list geometry used by note, task, purchase and secondary screens. */
internal object AppPageLayout {
    val editorListContentPadding = PaddingValues(
        top = AppSpacing.sm,
        bottom = AppSpacing.lg
    )
    val standardListContentPadding = PaddingValues(vertical = AppSpacing.xs)
    val toolbarHeight = 64.dp
}

/** Shared row geometry; leading/trailing controls may differ by row semantics. */
internal object AppRowDimensions {
    val statusGlyphSlot = 22.dp
    val controlTouchTarget = 40.dp
    val taskStatusToTextGap = 9.dp
    val taskTextToHandleGap = 22.dp
    val purchaseTextToCheckboxGap = 12.dp
    val purchaseCheckboxTouchTarget = 40.dp
    val purchaseCheckboxToHandleGap = 14.dp
}

@Composable
internal fun AppToolbarSurface(
    backgroundColor: Color,
    startInset: Dp = AppSpacing.toolbarControlInset,
    endInset: Dp = AppSpacing.toolbarControlInset,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        color = backgroundColor,
        contentColor = noteTextColor(backgroundColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(AppPageLayout.toolbarHeight)
                .padding(start = startInset, end = endInset),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppEditorTopBar(
    title: String,
    backgroundColor: Color,
    actions: @Composable RowScope.() -> Unit
) {
    val foreground = noteTextColor(backgroundColor)
    TopAppBar(
        title = {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                fontSize = AppTypography.screenTitle
            )
        },
        navigationIcon = {},
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = backgroundColor,
            scrolledContainerColor = backgroundColor,
            navigationIconContentColor = foreground,
            titleContentColor = foreground,
            actionIconContentColor = foreground
        )
    )
}

internal fun Modifier.appPageHorizontalInsets(): Modifier =
    padding(horizontal = AppSpacing.screenHorizontal)

internal fun Modifier.appPageVerticalInsets(): Modifier =
    padding(vertical = AppSpacing.screenVertical)

internal fun Modifier.appPageContentInsets(): Modifier =
    padding(
        horizontal = AppSpacing.screenHorizontal,
        vertical = AppSpacing.screenVertical
    )



/**
 * Shared main-list card dimensions.
 * contentHeight is a minimum; long first lines can increase the measured row height.
 */
internal object CardDimensions {
    val contentHeight = 88.dp
    val dividerThickness = 1.dp
    val rowHeight = contentHeight + dividerThickness

    // Align the visible handle with the first 22dp title line.
    // Keep a 40dp touch target centered on that visual position.
    val headerFirstLineHeight = 22.dp
    val handleTouchTargetSize = 40.dp
    val handleHitTargetTopOffset =
        AppSpacing.md + (headerFirstLineHeight - handleTouchTargetSize) / 2f
    // Keep the 40dp hit target centered on the handle, whose right edge now
    // aligns directly with the card metadata/date guide.
    val handleHitTargetRightInset = AppSpacing.screenHorizontal - 12.dp
    // Nested glyphs end at the row edge; center the 40.dp hit target on the 16.dp glyph.
    val nestedHandleHitTargetRightOverhang = 12.dp
    val nestedGroupMinHeight = 72.dp
}


internal fun formatNoteCreationDate(timestamp: Long): String {
    val locale = java.util.Locale("ru")
    val date = java.util.Date(timestamp)
    val target = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = java.util.Calendar.getInstance()
    val sameDay = target.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
        target.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
    if (sameDay) return "Сегодня"

    val yesterday = (today.clone() as java.util.Calendar).apply {
        add(java.util.Calendar.DAY_OF_YEAR, -1)
    }
    val isYesterday = target.get(java.util.Calendar.YEAR) == yesterday.get(java.util.Calendar.YEAR) &&
        target.get(java.util.Calendar.DAY_OF_YEAR) == yesterday.get(java.util.Calendar.DAY_OF_YEAR)
    if (isYesterday) return "Вчера"

    val pattern = if (target.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR)) {
        "EEE. dd.MM"
    } else {
        "EEE. dd.MM.yyyy"
    }
    return java.text.SimpleDateFormat(pattern, locale).format(date)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}
