package com.magnumold.notes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialDark = getPreferences(Context.MODE_PRIVATE).getBoolean("dark_v2", true)
        val savedThemeMode = getPreferences(Context.MODE_PRIVATE).getString("theme_mode", null)
            ?.takeIf { it in listOf("light", "dark", "custom") } ?: if (initialDark) "dark" else "light"
        val initialThemeColorSets = loadThemeColorSets(this)
        val initialThemeColors = themeColorsForMode(initialThemeColorSets, savedThemeMode)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(
            if (savedThemeMode == "custom") initialThemeColors.background.toArgbInt()
            else if (savedThemeMode == "dark") android.graphics.Color.BLACK else android.graphics.Color.WHITE
        ))
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val repository = NotesRepository(getPreferences(Context.MODE_PRIVATE))
        val recordsViewModel = ViewModelProvider(this, NotesViewModelFactory(repository))[NotesViewModel::class.java]
        setContent {
            NotesApp(
                activity = this@MainActivity,
                recordsViewModel = recordsViewModel,
                savedThemeMode = savedThemeMode,
                initialThemeColorSets = initialThemeColorSets
            )
        }
    }

    internal fun shareNote(note: Note) {
        val text = note.blocks.joinToString("\n\n") { (if (it.isChecklist) (if (it.checked) "☑ " else "☐ ") else "") + it.text }
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        startActivity(Intent.createChooser(intent, "Поделиться заметкой"))
    }

}
