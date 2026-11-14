package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLayoutContractTest {
    @Test
    fun approvedNotesHomeGeometryIsTheCanonicalContract() {
        assertEquals(20f, AppLayoutContract.pageHorizontalInset.value, 0f)
        assertEquals(16f, AppLayoutContract.pageVerticalInset.value, 0f)
        assertEquals(64f, AppLayoutContract.toolbarHeight.value, 0f)
        assertEquals(48f, AppLayoutContract.collectionRowHeight.value, 0f)
        assertEquals(88f, AppLayoutContract.mainCardMinHeight.value, 0f)
        assertEquals(16f, AppLayoutContract.mainCardVerticalInset.value, 0f)
        assertEquals(16f, AppLayoutContract.mainCardTextSize.value, 0f)
        assertEquals(18f, AppLayoutContract.pageTitleSize.value, 0f)
        assertEquals(40f, AppLayoutContract.controlTouchTarget.value, 0f)
        assertEquals(22f, AppLayoutContract.statusGlyphSlot.value, 0f)
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
