package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLayoutContractTest {
    @Test
    fun approvedNotesHomeGeometryIsTheCanonicalContract() {
        assertEquals(20f, AppLayoutContract.pageHorizontalInset.value, 0f)
        assertEquals(16f, AppLayoutContract.pageVerticalInset.value, 0f)
        assertEquals(64f, AppLayoutContract.toolbarHeight.value, 0f)
        assertEquals(20f, AppLayoutContract.toolbarLeadingInset.value, 0f)
        assertEquals(4f, AppLayoutContract.toolbarActionInset.value, 0f)
        assertEquals(48f, AppLayoutContract.collectionRowHeight.value, 0f)
        assertEquals(88f, AppLayoutContract.mainCardMinHeight.value, 0f)
        assertEquals(16f, AppLayoutContract.mainCardVerticalInset.value, 0f)
        assertEquals(16f, AppLayoutContract.mainCardTextSize.value, 0f)
        assertEquals(18f, AppLayoutContract.pageTitleSize.value, 0f)
        assertEquals(40f, AppLayoutContract.controlTouchTarget.value, 0f)
        assertEquals(22f, AppLayoutContract.statusGlyphSlot.value, 0f)
    }

    @Test
    fun cardHandlesAndNestedRowsShareOneGeometryContract() {
        assertEquals(16f, CardDimensions.handleVisualWidth.value, 0f)
        assertEquals(40f, CardDimensions.handleTouchTargetSize.value, 0f)
        assertEquals(32f, CardDimensions.sideRailWidth.value, 0f)
        assertEquals(28f, CardDimensions.sideRailControlSize.value, 0f)
        assertEquals(100f, CardDimensions.sideRailMinHeight.value, 0f)
        assertEquals(
            CardDimensions.handleHitTargetTopOffset + CardDimensions.handleTouchTargetSize / 2f,
            CardDimensions.sideRailTopInset + CardDimensions.sideRailControlSize / 2f
        )
        assertEquals(
            CardDimensions.handleHitTargetRightInset + CardDimensions.handleTouchTargetSize / 2f,
            CardDimensions.sideRailEndInset + CardDimensions.sideRailWidth / 2f
        )

        val visibleHandleCenterInset =
            AppSpacing.screenHorizontal.value + CardDimensions.handleVisualWidth.value / 2f
        val mainTouchTargetCenterInset =
            CardDimensions.handleHitTargetRightInset.value + CardDimensions.handleTouchTargetSize.value / 2f
        assertEquals(visibleHandleCenterInset, mainTouchTargetCenterInset, 0f)

        assertEquals(
            (CardDimensions.handleTouchTargetSize.value - CardDimensions.handleVisualWidth.value) / 2f,
            CardDimensions.nestedHandleHitTargetRightOverhang.value,
            0f
        )
        assertEquals(16f, CardDimensions.nestedRowVerticalInset.value, 0f)
        assertEquals(56f, CardDimensions.nestedRowContentMinHeight.value, 0f)
        assertEquals(88f, CardDimensions.nestedRowMinHeight.value, 0f)
        assertEquals(
            AppLayoutContract.mainCardMinHeight,
            CardDimensions.nestedRowMinHeight
        )
    }

    @Test
    fun editorListSpacingDoesNotDependOnSelectionState() {
        assertEquals(4f, AppPageLayout.editorListItemSpacing.value, 0f)
    }

    @Test
    fun compatibilityTokensResolveToTheCanonicalContract() {
        assertEquals(AppLayoutContract.pageHorizontalInset, AppSpacing.screenHorizontal)
        assertEquals(AppLayoutContract.toolbarHeight, AppPageLayout.toolbarHeight)
        assertEquals(AppLayoutContract.collectionRowHeight, AppCollectionRowDimensions.height)
        assertEquals(AppLayoutContract.editorBodyMinHeight, AppEditorDimensions.bodyMinHeight)
        assertEquals(AppLayoutContract.controlTouchTarget, AppRowDimensions.controlTouchTarget)
        assertEquals(AppLayoutContract.mainCardDividerThickness, CardDimensions.dividerThickness)
        assertEquals(AppLayoutContract.primaryAddButtonDiameter, PrimaryAddButtonDimensions.diameter)
    }
}
