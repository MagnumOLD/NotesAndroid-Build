package com.magnumold.notes

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color

/**
 * Synchronizes Compose theme state with Android window chrome and persisted
 * theme preferences. Kept outside the root navigation composable so the
 * Activity/window boundary has one explicit owner.
 */
@Composable
internal fun AppThemeEffects(
    activity: MainActivity,
    themeMode: String,
    dark: Boolean,
    themeColors: CustomThemeColors,
    themeColorSets: ThemeColorSets,
    noteColors: List<Color>,
    noteColorOpacities: NoteColorOpacities,
    themeSettings: ThemeSettingsStore
) {
    SideEffect {
        val appBackground = if (themeMode == "custom") themeColors.background
            else if (dark) Color.Black else Color.White
        val statusBarBackground = themeColors.statusBar
        activity.window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(appBackground.toArgbInt())
        )
        activity.window.statusBarColor = statusBarBackground.toArgbInt()
        val flags = activity.window.decorView.systemUiVisibility
        val darkStatusIcons = noteTextColor(statusBarBackground) == Color(0xFF202020)
        activity.window.decorView.systemUiVisibility = if (darkStatusIcons) {
            flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }

    LaunchedEffect(themeMode, dark) {
        activity.getPreferences(Context.MODE_PRIVATE).edit()
            .putString("theme_mode", themeMode)
            .putBoolean("dark_v2", dark)
            .apply()
    }
    LaunchedEffect(themeColorSets) {
        themeSettings.saveThemeColorSets(themeColorSets)
    }
    LaunchedEffect(noteColors) {
        themeSettings.saveNoteColors(noteColors)
    }
    LaunchedEffect(noteColorOpacities) {
        themeSettings.saveNoteColorOpacities(noteColorOpacities)
    }
}
