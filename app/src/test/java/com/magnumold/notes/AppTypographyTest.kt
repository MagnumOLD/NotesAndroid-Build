package com.magnumold.notes

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppTypographyTest {
    @Test
    fun materialTypographyUsesTheSystemSansSerifFamily() {
        val styles = listOf(
            AppMaterialTypography.displayLarge,
            AppMaterialTypography.displayMedium,
            AppMaterialTypography.displaySmall,
            AppMaterialTypography.headlineLarge,
            AppMaterialTypography.headlineMedium,
            AppMaterialTypography.headlineSmall,
            AppMaterialTypography.titleLarge,
            AppMaterialTypography.titleMedium,
            AppMaterialTypography.titleSmall,
            AppMaterialTypography.bodyLarge,
            AppMaterialTypography.bodyMedium,
            AppMaterialTypography.bodySmall,
            AppMaterialTypography.labelLarge,
            AppMaterialTypography.labelMedium,
            AppMaterialTypography.labelSmall
        )

        styles.forEach { assertEquals(FontFamily.Default, it.fontFamily) }
    }

    @Test
    fun noteAndCardTextUseSixteenSpWithTwentySixSpLineHeight() {
        assertEquals(16.sp, AppLayoutContract.mainCardTextSize)
        assertEquals(26.sp, AppLayoutContract.cardPreviewLineHeight)
        assertEquals(AppLayoutContract.cardPreviewLineHeight, AppTypography.cardPreviewLineHeight)
    }

    @Test
    fun compactCardDateMetadataKeepsItsIntentionalRailSize() {
        assertEquals(9.sp, AppLayoutContract.cardMetadataTextSize)
        assertEquals(12.sp, AppLayoutContract.cardMetadataLineHeight)
    }

    @Test
    fun compactTextRolesUseCompactLineHeights() {
        assertEquals(13.sp, AppLayoutContract.helperTextSize)
        assertEquals(18.sp, AppLayoutContract.helperLineHeight)
        assertEquals(12.sp, AppLayoutContract.metadataTextSize)
        assertEquals(18.sp, AppLayoutContract.metadataLineHeight)
        assertEquals(9.sp, AppLayoutContract.cardMetadataTextSize)
        assertEquals(12.sp, AppLayoutContract.cardMetadataLineHeight)
    }

    @Test
    fun semanticTypographyTokensResolveToTheCanonicalContract() {
        assertEquals(AppLayoutContract.pageTitleSize, AppTypography.screenTitle)
        assertEquals(AppLayoutContract.editorTitleSize, AppTypography.editorTitle)
        assertEquals(AppLayoutContract.sectionHeadingSize, AppTypography.sectionHeading)
        assertEquals(AppLayoutContract.bodyTextSize, AppTypography.body)
        assertEquals(AppLayoutContract.mainCardTextSize, AppTypography.cardPreview)
        assertEquals(AppLayoutContract.helperTextSize, AppTypography.helper)
        assertEquals(AppLayoutContract.helperLineHeight, AppTypography.helperLineHeight)
        assertEquals(AppLayoutContract.sectionLabelSize, AppTypography.sectionLabel)
        assertEquals(AppLayoutContract.metadataTextSize, AppTypography.metadata)
        assertEquals(AppLayoutContract.metadataLineHeight, AppTypography.metadataLineHeight)
        assertEquals(AppLayoutContract.cardMetadataTextSize, AppTypography.cardMetadata)
        assertEquals(AppLayoutContract.cardMetadataLineHeight, AppTypography.cardMetadataLineHeight)
    }

    @Test
    fun coreTextRolesHaveExplicitSizesAndLineHeights() {
        assertEquals(16.sp, AppMaterialTypography.bodyLarge.fontSize)
        assertEquals(24.sp, AppMaterialTypography.bodyLarge.lineHeight)
        assertEquals(14.sp, AppMaterialTypography.bodyMedium.fontSize)
        assertEquals(20.sp, AppMaterialTypography.bodyMedium.lineHeight)
        assertEquals(13.sp, AppMaterialTypography.bodySmall.fontSize)
        assertEquals(18.sp, AppMaterialTypography.bodySmall.lineHeight)
        assertEquals(18.sp, AppMaterialTypography.titleLarge.fontSize)
        assertEquals(24.sp, AppMaterialTypography.titleLarge.lineHeight)
        assertEquals(14.sp, AppMaterialTypography.labelLarge.fontSize)
        assertEquals(20.sp, AppMaterialTypography.labelLarge.lineHeight)
    }
}
