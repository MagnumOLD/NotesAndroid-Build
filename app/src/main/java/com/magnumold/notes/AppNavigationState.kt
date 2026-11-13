package com.magnumold.notes

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Owns transient navigation and view state for the app's top-level screens.
 * It deliberately contains no persistence or UI logic.
 */
@Stable
internal class AppNavigationState {
    var activeCollectionId by mutableStateOf<Long?>(null)
    var selectedId by mutableStateOf<Long?>(null)
    var autoFocusNewNote by mutableStateOf(false)
    var search by mutableStateOf("")
    var searchVisible by mutableStateOf(false)
    var settingsVisible by mutableStateOf(false)
    var vaultScreen by mutableStateOf<String?>(null)
}
