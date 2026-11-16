package com.magnumold.notes

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemePrimaryTextColorTest {
    @Test
    fun lightThemeUsesApprovedPrimaryTextColor() {
        assertEquals(Color(0xFF111111), lightPrimaryTextColor)
    }

    @Test
    fun darkThemeUsesApprovedPrimaryTextColor() {
        assertEquals(Color(0xFFF5F5F5), darkPrimaryTextColor)
    }
}
