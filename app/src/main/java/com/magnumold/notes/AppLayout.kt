package com.magnumold.notes

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Canonical layout contract derived from the approved NotesHome screen.
 *
 * Keep these values as the single source of truth for shared geometry.
 * Screen-specific components may expose semantic aliases, but must not
 * redefine the underlying measurements independently.
 *
 * NotesHome intentionally remains unchanged while this contract is introduced:
 * its current literal dimensions match these values exactly.
 */
internal object AppLayoutContract {
    // Base spacing scale.
    val space2xs = 4.dp
    val spaceXs = 8.dp
    val spaceSm = 12.dp
    val spaceMd = 16.dp
    val spaceLg = 20.dp
    val spaceXl = 24.dp
    val space2xl = 32.dp

    // Main page guides and toolbar geometry.
    val pageHorizontalInset = spaceLg
    val pageVerticalInset = spaceMd
    val toolbarLeadingInset = pageHorizontalInset
    val toolbarActionInset = space2xs
    val toolbarHeight = 64.dp

    // Main list geometry from the approved NotesHome layout.
    val collectionRowHeight = 48.dp
    val mainCardMinHeight = 88.dp
    val mainCardVerticalInset = spaceMd
    val mainCardTextSize = 16.sp
    val pageTitleSize = 18.sp
    val collectionLabelSize = 14.sp

    // Typography roles shared by all features.
    val editorTitleSize = 18.sp
    val sectionHeadingSize = 17.sp
    val bodyTextSize = 16.sp
    val cardPreviewLineHeight = 22.sp
    val helperTextSize = 13.sp
    val bodyLineHeight = 24.sp
    val sectionLabelSize = collectionLabelSize
    val sectionLabelLineHeight = 20.sp
    val metadataTextSize = 12.sp

    // Semantic editor geometry.
    val editorTitleMinHeight = 48.dp
    val editorDescriptionMinHeight = 72.dp
    val editorBodyMinHeight = 160.dp

    // Shared row geometry; gaps remain role-specific.
    val controlTouchTarget = 40.dp
    val statusGlyphSlot = 22.dp
    val taskStatusToTextGap = 9.dp
    val taskTextToHandleGap = 22.dp
    val purchaseTextToCheckboxGap = 12.dp
    val purchaseCheckboxToHandleGap = 14.dp

    // Main-card geometry.
    val mainCardDividerThickness = 1.dp
    val mainCardHeaderFirstLineHeight = 22.dp
    val nestedGroupMinHeight = 72.dp

    // Primary add control geometry.
    val primaryAddButtonDiameter = 56.dp
    val primaryAddButtonVerticalOffset = (-65).dp
}

/** Shared geometry for every primary circular add button. */
internal object PrimaryAddButtonDimensions {
    val diameter = AppLayoutContract.primaryAddButtonDiameter
    val verticalOffset = AppLayoutContract.primaryAddButtonVerticalOffset
}

/**
 * Compatibility names for existing screens.
 * New layout code should prefer AppLayoutContract or semantic dimensions below.
 */
internal object AppSpacing {
    val xxs = AppLayoutContract.space2xs
    val xs = AppLayoutContract.spaceXs
    val sm = AppLayoutContract.spaceSm
    val md = AppLayoutContract.spaceMd
    val lg = AppLayoutContract.spaceLg
    val xl = AppLayoutContract.spaceXl
    val xxl = AppLayoutContract.space2xl

    val screenHorizontal = AppLayoutContract.pageHorizontalInset
    val screenVertical = AppLayoutContract.pageVerticalInset
    val toolbarControlInset = AppLayoutContract.toolbarActionInset
    // Material3 ListItem has a 16.dp default content inset; 4.dp outside
    // aligns its visible content with the app's 20.dp page guide.
    val settingsListItemOuterInset = xxs
}

/**
 * App-wide typography tokens. Screen-specific hierarchy may differ, but the
 * underlying sizes and line heights are defined here rather than duplicated.
 */
internal object AppTypography {
    val screenTitle = AppLayoutContract.pageTitleSize
    val editorTitle = AppLayoutContract.editorTitleSize
    val sectionHeading = AppLayoutContract.sectionHeadingSize
    val body = AppLayoutContract.bodyTextSize
    val cardPreview = AppLayoutContract.mainCardTextSize
    val cardPreviewLineHeight = AppLayoutContract.cardPreviewLineHeight
    val helper = AppLayoutContract.helperTextSize
    val bodyLineHeight = AppLayoutContract.bodyLineHeight
    val sectionLabel = AppLayoutContract.sectionLabelSize
    val sectionLabelLineHeight = AppLayoutContract.sectionLabelLineHeight
    val metadata = AppLayoutContract.metadataTextSize

    // Semantic text styles are aliases of the app-wide Material typography.
    // This keeps screen code from rebuilding equivalent TextStyle values.
    @Composable
    fun screenTitleStyle(): TextStyle = MaterialTheme.typography.titleLarge

    @Composable
    fun editorTitleStyle(): TextStyle = MaterialTheme.typography.titleLarge

    @Composable
    fun sectionHeadingStyle(): TextStyle =
        MaterialTheme.typography.titleMedium.copy(fontSize = sectionHeading)

    @Composable
    fun bodyStyle(): TextStyle = MaterialTheme.typography.bodyLarge

    @Composable
    fun cardPreviewStyle(): TextStyle =
        MaterialTheme.typography.bodyLarge.copy(lineHeight = cardPreviewLineHeight)

    @Composable
    fun sectionLabelStyle(): TextStyle = MaterialTheme.typography.labelLarge

    @Composable
    fun helperStyle(): TextStyle = MaterialTheme.typography.bodySmall

    @Composable
    fun metadataStyle(): TextStyle = MaterialTheme.typography.labelSmall
}

/** Semantic dimensions for the common page and list structures. */
internal object AppPageLayout {
    val editorListContentPadding = PaddingValues(
        top = AppSpacing.sm,
        bottom = AppSpacing.lg
    )
    val standardListContentPadding = PaddingValues(vertical = AppSpacing.xs)
    val toolbarHeight = AppLayoutContract.toolbarHeight
}

/** Semantic minimum sizes used inside record editors; visual values stay unchanged. */
internal object AppEditorDimensions {
    val taskTitleMinHeight = AppLayoutContract.editorTitleMinHeight
    val descriptionMinHeight = AppLayoutContract.editorDescriptionMinHeight
    val bodyMinHeight = AppLayoutContract.editorBodyMinHeight
}

internal object AppCollectionRowDimensions {
    val height = AppLayoutContract.collectionRowHeight
}

/** Shared row geometry; leading/trailing controls may differ by row semantics. */
internal object AppRowDimensions {
    val statusGlyphSlot = AppLayoutContract.statusGlyphSlot
    val controlTouchTarget = AppLayoutContract.controlTouchTarget
    val taskStatusToTextGap = AppLayoutContract.taskStatusToTextGap
    val taskTextToHandleGap = AppLayoutContract.taskTextToHandleGap
    val purchaseTextToCheckboxGap = AppLayoutContract.purchaseTextToCheckboxGap
    val purchaseCheckboxTouchTarget = AppLayoutContract.controlTouchTarget
    val purchaseCheckboxToHandleGap = AppLayoutContract.purchaseCheckboxToHandleGap
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

/**
 * Shared page frame. Feature screens provide content and actions; this layer
 * owns the common Scaffold contract without taking ownership of feature state.
 */
@Composable
internal fun AppPageScaffold(
    modifier: Modifier = Modifier,
    containerColor: Color,
    topBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        containerColor = containerColor,
        topBar = topBar,
        floatingActionButton = floatingActionButton,
        content = content
    )
}

/**
 * Shared top-bar surface and Material3 behavior for page-level headers.
 * Feature-specific title fields and actions remain supplied through slots.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppPageTopBar(
    backgroundColor: Color,
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    navigationIcon: @Composable () -> Unit = {}
) {
    val foreground = noteTextColor(backgroundColor)
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
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

@Composable
internal fun AppEditorTopBar(
    title: String,
    backgroundColor: Color,
    actions: @Composable RowScope.() -> Unit
) {
    AppPageTopBar(
        backgroundColor = backgroundColor,
        title = {
            Text(
                title,
                style = AppTypography.screenTitleStyle()
            )
        },
        actions = actions
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
    val contentHeight = AppLayoutContract.mainCardMinHeight
    val dividerThickness = AppLayoutContract.mainCardDividerThickness
    val rowHeight = contentHeight + dividerThickness

    // Align the visible handle with the first title line.
    // Keep a 40dp touch target centered on that visual position.
    val headerFirstLineHeight = AppLayoutContract.mainCardHeaderFirstLineHeight
    val handleTouchTargetSize = AppLayoutContract.controlTouchTarget
    val handleHitTargetTopOffset =
        AppSpacing.md + (headerFirstLineHeight - handleTouchTargetSize) / 2f
    // Keep the 40dp hit target centered on the handle, whose right edge now
    // aligns directly with the card metadata/date guide.
    val handleHitTargetRightInset = AppSpacing.screenHorizontal - 12.dp
    // Nested glyphs end at the row edge; center the 40.dp hit target on the 16.dp glyph.
    val nestedHandleHitTargetRightOverhang = 12.dp
    val nestedGroupMinHeight = AppLayoutContract.nestedGroupMinHeight
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
