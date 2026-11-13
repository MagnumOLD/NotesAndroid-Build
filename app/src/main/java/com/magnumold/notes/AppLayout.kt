package com.magnumold.notes

import androidx.compose.ui.unit.dp

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
